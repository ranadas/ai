package com.example.invoice.integrations;

import com.example.invoice.config.InvoiceProperties;
import com.example.invoice.domain.ClientNotificationResult;
import com.example.invoice.domain.InvoiceProcessResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Component
public class ClientCallbackClient {
    private final InvoiceProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public ClientCallbackClient(InvoiceProperties properties, HttpClient httpClient, ObjectMapper mapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    public ClientNotificationResult send(InvoiceProcessResponse response, String overrideUrl) {
        String target = overrideUrl == null || overrideUrl.isBlank() ? properties.clientCallback().baseUrl() : overrideUrl;
        String outcome = response.errors().isEmpty() && response.geneva() != null && response.geneva().posted() ? "SUCCESS" : "FAILURE";
        String invoiceReference = response.extracted() != null && response.extracted().invoiceNumber() != null
                ? response.extracted().invoiceNumber()
                : response.invoiceId();
        String message = "SUCCESS".equals(outcome)
                ? "Payment confirmed for invoice " + invoiceReference + "."
                : "Payment rejected or requires action for invoice " + invoiceReference + ": " + String.join(", ", response.errors());

        if (!properties.clientCallback().enabled() && (overrideUrl == null || overrideUrl.isBlank())) {
            return new ClientNotificationResult(false, outcome, "DRY_RUN", message);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(trimTrailingSlash(target) + "/api/payment-status"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.clientCallback().apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                            "invoiceId", response.invoiceId(),
                            "outcome", outcome,
                            "message", message
                    ))))
                    .build();
            HttpResponse<String> http = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new ClientNotificationResult(http.statusCode() >= 200 && http.statusCode() <= 299, outcome, "HTTP", message);
        } catch (Exception ex) {
            return new ClientNotificationResult(false, outcome, "HTTP", "Client callback failed: " + ex.getMessage());
        }
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
