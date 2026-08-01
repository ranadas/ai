package com.example.invoiceagent.api;

public record ClientNotificationResult(
        boolean success,
        String statusCode,
        String message
) {
}
