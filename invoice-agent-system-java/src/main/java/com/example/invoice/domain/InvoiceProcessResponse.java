package com.example.invoice.domain;

import java.util.List;

public record InvoiceProcessResponse(
        String invoiceId,
        ProcessingStatus status,
        ExtractedInvoice extracted,
        ValidationResult validation,
        ReviewResult review,
        GenevaPostResult geneva,
        NavControlPack navControlPack,
        ClientNotificationResult clientMessage,
        List<String> errors,
        List<AuditEvent> audit
) {
    public InvoiceProcessResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
        audit = audit == null ? List.of() : List.copyOf(audit);
    }

    public InvoiceProcessResponse withClientNotification(ClientNotificationResult notification, List<AuditEvent> auditEvents) {
        ProcessingStatus finalStatus = "SUCCESS".equals(notification.outcome()) ? ProcessingStatus.CLIENT_NOTIFIED : status;
        return new InvoiceProcessResponse(
                invoiceId,
                finalStatus,
                extracted,
                validation,
                review,
                geneva,
                navControlPack,
                notification,
                errors,
                auditEvents
        );
    }
}
