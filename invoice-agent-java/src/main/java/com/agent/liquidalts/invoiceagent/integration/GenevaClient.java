package com.agent.liquidalts.invoiceagent.integration;

import com.agent.liquidalts.invoiceagent.config.InvoiceAgentProperties;
import com.agent.liquidalts.invoiceagent.domain.InvoiceData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Downstream integration with Geneva via its REST API.
 * Uses Spring's {@link RestClient} with explicit timeouts and an
 * Idempotency-Key header so retries never double-post.
 *
 * Productionisation: wrap {@link #upsertInvoice} with Resilience4j
 * (circuit breaker + retry reusing the same idempotency key).
 */
@Component
public class GenevaClient {

    private static final Logger log = LoggerFactory.getLogger(GenevaClient.class);

    private final RestClient client;

    public GenevaClient(InvoiceAgentProperties props) {
        var geneva = props.geneva();
        this.client = RestClient.builder()
                .baseUrl(geneva.baseUrl())
                .defaultHeader("X-Api-Key", geneva.apiKey())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(
                        ClientHttpRequestFactorySettings.defaults()
                                .withConnectTimeout(geneva.connectTimeout())
                                .withReadTimeout(geneva.readTimeout())))
                .build();
    }

    public GenevaInvoiceResponse upsertInvoice(GenevaInvoiceRequest request, String idempotencyKey) {
        log.info("Pushing invoice {} to Geneva (idempotencyKey={})", request.invoiceNumber(), idempotencyKey);
        GenevaInvoiceResponse response = client.post()
                .uri("/api/v1/invoices")
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new GenevaIntegrationException(
                            "Geneva rejected invoice %s: HTTP %s"
                                    .formatted(request.invoiceNumber(), res.getStatusCode()));
                })
                .body(GenevaInvoiceResponse.class);

        if (response == null) {
            throw new GenevaIntegrationException(
                    "Empty response from Geneva for " + request.invoiceNumber());
        }
        return response;
    }

    public static GenevaInvoiceRequest toRequest(String externalReference, InvoiceData data, String vendorId) {
        return new GenevaInvoiceRequest(
                externalReference,
                vendorId,
                data.invoiceNumber(),
                data.currency(),
                data.netAmount(),
                data.taxAmount(),
                data.grossAmount(),
                data.poNumber()
        );
    }
}
