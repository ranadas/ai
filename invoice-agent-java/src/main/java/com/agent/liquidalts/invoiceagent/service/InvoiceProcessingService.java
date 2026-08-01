package com.agent.liquidalts.invoiceagent.service;

import com.agent.liquidalts.invoiceagent.audit.AuditTrail;
import com.agent.liquidalts.invoiceagent.domain.InvoiceRecord;
import com.agent.liquidalts.invoiceagent.store.InvoiceStore;
import org.springframework.stereotype.Service;

/**
 * Synchronous entry point for submissions. Accepts, persists and audits the
 * invoice, then hands off to the async {@link InvoicePipeline} — the caller
 * gets 202 semantics regardless of how long the agents take.
 */
@Service
public class InvoiceProcessingService {

    private final InvoiceStore store;
    private final InvoicePipeline pipeline;
    private final AuditTrail audit;

    public InvoiceProcessingService(InvoiceStore store, InvoicePipeline pipeline, AuditTrail audit) {
        this.store = store;
        this.pipeline = pipeline;
        this.audit = audit;
    }

    /** Idempotent on the Idempotency-Key: duplicates return the original record. */
    public InvoiceRecord submit(String idempotencyKey, String submittedBy, String fileName, byte[] pdfBytes) {
        var existing = store.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            InvoiceRecord duplicate = existing.get();
            audit.record(duplicate.id(), submittedBy, "DUPLICATE_SUBMISSION", "Idempotency-Key already seen");
            return duplicate;
        }

        InvoiceRecord record = store.save(
                InvoiceRecord.newSubmission(idempotencyKey, submittedBy, fileName, pdfBytes));
        audit.record(record.id(), submittedBy, "INVOICE_SUBMITTED",
                "file=%s, size=%dB".formatted(fileName, pdfBytes.length));
        pipeline.runIntakePipeline(record.id());
        return record;
    }
}
