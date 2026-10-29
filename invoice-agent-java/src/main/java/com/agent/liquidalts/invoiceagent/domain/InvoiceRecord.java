package com.agent.liquidalts.invoiceagent.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root tracked through the pipeline. Immutable: every state
 * change produces a new instance via {@link #toBuilder()}, which makes
 * the record safe to hand across pipeline threads and trivially
 * compatible with {@code ConcurrentHashMap.compute}.
 */
public record InvoiceRecord(
        UUID id,
        String idempotencyKey,
        String submittedBy,
        String fileName,
        byte[] pdfBytes,
        InvoiceStatus status,
        InvoiceData extractedData,
        ValidationReport validationReport,
        String reviewSummary,
        ReviewDecision reviewDecision,
        String genevaReference,
        String controlPack,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {

    public static InvoiceRecord newSubmission(String idempotencyKey, String submittedBy,
                                              String fileName, byte[] pdfBytes) {
        Instant now = Instant.now();
        return new InvoiceRecord(UUID.randomUUID(), idempotencyKey, submittedBy, fileName, pdfBytes,
                InvoiceStatus.RECEIVED, null, null, null, null, null, null, null, now, now);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    /** Copy-on-write builder; {@code updatedAt} is always refreshed on build. */
    public static final class Builder {
        private final InvoiceRecord base;
        private InvoiceStatus status;
        private InvoiceData extractedData;
        private ValidationReport validationReport;
        private String reviewSummary;
        private ReviewDecision reviewDecision;
        private String genevaReference;
        private String controlPack;
        private String failureReason;

        private Builder(InvoiceRecord base) {
            this.base = base;
            this.status = base.status;
            this.extractedData = base.extractedData;
            this.validationReport = base.validationReport;
            this.reviewSummary = base.reviewSummary;
            this.reviewDecision = base.reviewDecision;
            this.genevaReference = base.genevaReference;
            this.controlPack = base.controlPack;
            this.failureReason = base.failureReason;
        }

        public Builder status(InvoiceStatus status)                        { this.status = status; return this; }
        public Builder extractedData(InvoiceData data)                     { this.extractedData = data; return this; }
        public Builder validationReport(ValidationReport report)           { this.validationReport = report; return this; }
        public Builder reviewSummary(String summary)                       { this.reviewSummary = summary; return this; }
        public Builder reviewDecision(ReviewDecision decision)             { this.reviewDecision = decision; return this; }
        public Builder genevaReference(String reference)                   { this.genevaReference = reference; return this; }
        public Builder controlPack(String pack)                            { this.controlPack = pack; return this; }
        public Builder failureReason(String reason)                        { this.failureReason = reason; return this; }

        public InvoiceRecord build() {
            return new InvoiceRecord(base.id, base.idempotencyKey, base.submittedBy, base.fileName,
                    base.pdfBytes, status, extractedData, validationReport, reviewSummary,
                    reviewDecision, genevaReference, controlPack, failureReason,
                    base.createdAt, Instant.now());
        }
    }

    // byte[] breaks record equality semantics; identity is the id.
    @Override
    public boolean equals(Object other) {
        return other instanceof InvoiceRecord that && that.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
