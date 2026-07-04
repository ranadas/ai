package com.example.invoiceagent.api;

public record NavControlPackResult(
        boolean success,
        String location,
        String message
) {
}
