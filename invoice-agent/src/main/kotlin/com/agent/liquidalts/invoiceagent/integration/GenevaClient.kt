package com.agent.liquidalts.invoiceagent.integration

import com.agent.liquidalts.invoiceagent.config.InvoiceAgentProperties
import com.agent.liquidalts.invoiceagent.domain.InvoiceData
import org.slf4j.LoggerFactory
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal

data class GenevaInvoiceRequest(
    val externalReference: String,
    val vendorId: String,
    val invoiceNumber: String,
    val currency: String,
    val netAmount: BigDecimal,
    val taxAmount: BigDecimal,
    val grossAmount: BigDecimal,
    val poNumber: String?,
)

data class GenevaInvoiceResponse(
    val genevaReference: String,
    val status: String,
)

class GenevaIntegrationException(message: String) : RuntimeException(message)

/**
 * Downstream integration with Geneva via its REST API.
 * Uses Spring's [RestClient] with explicit timeouts and an
 * Idempotency-Key header so retries never double-post.
 */
@Component
class GenevaClient(props: InvoiceAgentProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val client: RestClient = RestClient.builder()
        .baseUrl(props.geneva.baseUrl)
        .defaultHeader("X-Api-Key", props.geneva.apiKey)
        .requestFactory(
            ClientHttpRequestFactoryBuilder.detect().build(
                ClientHttpRequestFactorySettings.defaults()
                    .withConnectTimeout(props.geneva.connectTimeout)
                    .withReadTimeout(props.geneva.readTimeout)
            )
        )
        .build()

    fun upsertInvoice(request: GenevaInvoiceRequest, idempotencyKey: String): GenevaInvoiceResponse {
        log.info("Pushing invoice {} to Geneva (idempotencyKey={})", request.invoiceNumber, idempotencyKey)
        return client.post()
            .uri("/api/v1/invoices")
            .header("Idempotency-Key", idempotencyKey)
            .body(request)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { _, response ->
                throw GenevaIntegrationException(
                    "Geneva rejected invoice ${request.invoiceNumber}: HTTP ${response.statusCode}"
                )
            }
            .body(GenevaInvoiceResponse::class.java)
            ?: throw GenevaIntegrationException("Empty response from Geneva for ${request.invoiceNumber}")
    }

    companion object {
        fun toRequest(externalReference: String, data: InvoiceData, vendorId: String) =
            GenevaInvoiceRequest(
                externalReference = externalReference,
                vendorId = vendorId,
                invoiceNumber = data.invoiceNumber,
                currency = data.currency,
                netAmount = data.netAmount,
                taxAmount = data.taxAmount,
                grossAmount = data.grossAmount,
                poNumber = data.poNumber,
            )
    }
}
