package com.example.invoice.domain;

import java.time.Instant;
import java.util.Map;

public record NavControlPack(
        String controlPackId,
        String invoiceId,
        Instant createdAt,
        String status,
        Map<String, String> checksSummary,
        String paymentConfirmation
) {
    public NavControlPack {
        checksSummary = checksSummary == null ? Map.of() : Map.copyOf(checksSummary);
    }
}
