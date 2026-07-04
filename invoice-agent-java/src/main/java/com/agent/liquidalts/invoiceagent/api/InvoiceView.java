package com.agent.liquidalts.invoiceagent.api;

import com.agent.liquidalts.invoiceagent.domain.Finding;
import com.agent.liquidalts.invoiceagent.domain.InvoiceData;
import com.agent.liquidalts.invoiceagent.domain.InvoiceRecord;
import com.agent.liquidalts.invoiceagent.domain.InvoiceStatus;
import com.agent.liquidalts.invoiceagent.domain.ReviewDecision;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read model exposed by the API — the raw PDF bytes never leave the service. */
public record InvoiceView(
        UUID invoiceId,
        InvoiceStatus status,
        String fileName,
        String submittedBy,
        InvoiceData extractedData,
        Boolean validationPassed,
        List<Finding> findings,
        String reviewSummary,
        ReviewDecision reviewDecision,
        String genevaReference,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static InvoiceView from(InvoiceRecord r) {
        return new InvoiceView(
                r.id(),
                r.status(),
                r.fileName(),
                r.submittedBy(),
                r.extractedData(),
                r.validationReport() != null ? r.validationReport().passed() : null,
                r.validationReport() != null ? r.validationReport().findings() : List.of(),
                r.reviewSummary(),
                r.reviewDecision(),
                r.genevaReference(),
                r.failureReason(),
                r.createdAt(),
                r.updatedAt()
        );
    }
}
