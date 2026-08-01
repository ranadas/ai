package com.example.invoice.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InvoiceContext {
    private final String invoiceId;
    private final String source;
    private final String fileName;
    private final String contentType;
    private final byte[] pdfBytes;
    private final String manualText;
    private final String clientCallbackUrl;
    private final Map<String, String> metadata;
    private final Instant startedAt;
    private final List<AuditEvent> audit = new ArrayList<>();

    public InvoiceContext(
            String source,
            String fileName,
            String contentType,
            byte[] pdfBytes,
            String manualText,
            String clientCallbackUrl,
            Map<String, String> metadata
    ) {
        this.invoiceId = UUID.randomUUID().toString();
        this.source = source;
        this.fileName = fileName;
        this.contentType = contentType;
        this.pdfBytes = pdfBytes == null ? null : pdfBytes.clone();
        this.manualText = manualText;
        this.clientCallbackUrl = clientCallbackUrl;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.startedAt = Instant.now();
    }

    public void record(String agent, String action, String outcome) {
        audit.add(new AuditEvent(Instant.now(), agent, action, outcome));
    }

    public String invoiceId() { return invoiceId; }
    public String source() { return source; }
    public String fileName() { return fileName; }
    public String contentType() { return contentType; }
    public byte[] pdfBytes() { return pdfBytes == null ? null : pdfBytes.clone(); }
    public String manualText() { return manualText; }
    public String clientCallbackUrl() { return clientCallbackUrl; }
    public Map<String, String> metadata() { return metadata; }
    public Instant startedAt() { return startedAt; }
    public List<AuditEvent> audit() { return List.copyOf(audit); }
}
