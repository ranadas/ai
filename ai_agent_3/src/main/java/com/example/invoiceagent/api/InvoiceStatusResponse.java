package com.example.invoiceagent.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.example.invoiceagent.domain.AuditEvent;
import com.example.invoiceagent.domain.InvoiceProcess;
import com.example.invoiceagent.domain.ProcessStatus;

public record InvoiceStatusResponse(
        UUID processId,
        String clientId,
        String invoiceReference,
        ProcessStatus status,
        String invoiceNumber,
        String supplierName,
        String currency,
        BigDecimal amount,
        LocalDate dueDate,
        String message,
        String genevaCorrelationId,
        String navControlPackLocation,
        String clientCallbackStatus,
        Instant createdAt,
        Instant updatedAt,
        List<AuditEntry> audit
) {
    public static InvoiceStatusResponse from(InvoiceProcess process, List<AuditEvent> auditEvents) {
        return new InvoiceStatusResponse(
                process.getId(),
                process.getClientId(),
                process.getInvoiceReference(),
                process.getStatus(),
                process.getInvoiceNumber(),
                process.getSupplierName(),
                process.getCurrency(),
                process.getAmount(),
                process.getDueDate(),
                process.getMessage(),
                process.getGenevaCorrelationId(),
                process.getNavControlPackLocation(),
                process.getClientCallbackStatus(),
                process.getCreatedAt(),
                process.getUpdatedAt(),
                auditEvents.stream().map(AuditEntry::from).toList()
        );
    }

    public record AuditEntry(String step, String status, String message, Instant createdAt) {
        static AuditEntry from(AuditEvent event) {
            return new AuditEntry(event.getStep(), event.getStatus(), event.getMessage(), event.getCreatedAt());
        }
    }
}
