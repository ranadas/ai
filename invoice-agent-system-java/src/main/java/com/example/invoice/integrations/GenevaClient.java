package com.example.invoice.integrations;

import com.example.invoice.config.InvoiceProperties;
import com.example.invoice.domain.ExtractedInvoice;
import com.example.invoice.domain.GenevaPostResult;
import com.example.invoice.domain.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class GenevaClient {
    private final InvoiceProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public GenevaClient(InvoiceProperties properties, HttpClient httpClient, ObjectMapper mapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    public GenevaPostResult postInvoice(String invoiceId, ExtractedInvoice extracted, ValidationResult validation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("invoiceId", invoiceId);
        payload.put("invoiceNumber", extracted.invoiceNumber());
        payload.put("vendorId", extracted.vendorId());
        payload.put("vendorName", extracted.vendorName());
        payload.put("currency", extracted.currency());
        payload.put("grossAmount", extracted.grossAmount());
        payload.put("paymentReference", extracted.paymentReference());
        payload.put("validated", validation.valid());
        payload.put("enrichedFields", validation.enrichedFields());

        InvoiceProperties.Geneva cfg = properties.geneva();
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(trimTrailingSlash(cfg.baseUrl()) + "/api/invoices"))
                    .timeout(Duration.ofSeconds(cfg.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
        } catch (Exception ex) {
            return new GenevaPostResult(false, null, "Geneva request build failed: " + ex.getMessage(), payload);
        }

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean posted = response.statusCode() >= 200 && response.statusCode() <= 299;
            return new GenevaPostResult(
                    posted,
                    posted ? "GEN-" + UUID.randomUUID() : null,
                    "HTTP " + response.statusCode(),
                    payload
            );
        } catch (Exception ex) {
            return new GenevaPostResult(false, null, "Geneva call failed: " + ex.getMessage(), payload);
        }
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
