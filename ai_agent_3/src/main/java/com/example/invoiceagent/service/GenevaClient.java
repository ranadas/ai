package com.example.invoiceagent.service;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.example.invoiceagent.api.GenevaUpdateResult;
import com.example.invoiceagent.api.InvoiceExtraction;
import com.example.invoiceagent.config.InvoiceAgentProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GenevaClient {

    private final RestClient restClient;
    private final InvoiceAgentProperties properties;

    public GenevaUpdateResult updateInvoice(UUID processId, String clientId, InvoiceExtraction extraction) {
        if (properties.geneva().dryRun()) {
            return new GenevaUpdateResult(true, "dry-run-" + processId, "Geneva update skipped because dry-run is enabled");
        }

        GenevaUpdateRequest request = new GenevaUpdateRequest(
                processId.toString(),
                clientId,
                extraction.invoiceNumber(),
                extraction.supplierName(),
                extraction.currency(),
                extraction.amount(),
                extraction.dueDate(),
                extraction.paymentReference()
        );

        GenevaUpdateResponse response = restClient.post()
                .uri(properties.geneva().baseUrl() + properties.geneva().updatePath())
                .body(request)
                .retrieve()
                .body(GenevaUpdateResponse.class);

        if (response == null || !response.success()) {
            String message = response == null ? "Geneva returned no response" : response.message();
            return new GenevaUpdateResult(false, null, message);
        }
        return new GenevaUpdateResult(true, response.correlationId(), response.message());
    }

    record GenevaUpdateRequest(
            String processId,
            String clientId,
            String invoiceNumber,
            String supplierName,
            String currency,
            java.math.BigDecimal amount,
            java.time.LocalDate dueDate,
            String paymentReference
    ) {
    }

    record GenevaUpdateResponse(boolean success, String correlationId, String message) {
    }
}
