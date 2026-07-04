package com.example.invoice.domain;

import java.util.Map;

public record InvoiceProcessRequest(
        String source,
        String fileName,
        String contentType,
        String pdfBase64,
        String manualText,
        String clientCallbackUrl,
        Map<String, String> metadata
) {
    public InvoiceProcessRequest {
        source = source == null || source.isBlank() ? "REST_JSON" : source;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static InvoiceProcessRequest fromManualText(String manualText) {
        return new InvoiceProcessRequest("REST_JSON", null, null, null, manualText, null, Map.of());
    }
}
