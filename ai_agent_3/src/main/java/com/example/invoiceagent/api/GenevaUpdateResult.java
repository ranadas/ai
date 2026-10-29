package com.example.invoiceagent.api;

public record GenevaUpdateResult(
        boolean success,
        String correlationId,
        String message
) {
}
