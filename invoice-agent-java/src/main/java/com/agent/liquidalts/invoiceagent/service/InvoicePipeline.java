package com.agent.liquidalts.invoiceagent.service;

import com.agent.liquidalts.invoiceagent.agent.InvoiceAgentFactory;
import com.agent.liquidalts.invoiceagent.audit.AuditTrail;
import com.agent.liquidalts.invoiceagent.domain.InvoiceData;
import com.agent.liquidalts.invoiceagent.domain.InvoiceRecord;
import com.agent.liquidalts.invoiceagent.domain.InvoiceStatus;
import com.agent.liquidalts.invoiceagent.extraction.PdfTextExtractor;
import com.agent.liquidalts.invoiceagent.store.InvoiceStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The asynchronous halves of the pipeline. Lives in its own bean (rather
 * than inside InvoiceProcessingService) so {@code @Async} proxying works —
 * self-invocation would bypass the proxy and run agents on servlet threads.
 *
 * Intake pipeline:  RECEIVED -> EXTRACTING -> VALIDATING -> PENDING_REVIEW
 * Posting pipeline: POSTING  -> COMPLETED | POSTING_FAILED
 *
 * The human four-eye gate sits between the two; nothing crosses it without
 * an explicit reviewer decision (see ReviewService).
 */
@Component
public class InvoicePipeline {

    private static final Logger log = LoggerFactory.getLogger(InvoicePipeline.class);

    private final InvoiceStore store;
    private final InvoiceAgentFactory agents;
    private final PdfTextExtractor pdfExtractor;
    private final AuditTrail audit;
    private final ObjectMapper objectMapper;

    public InvoicePipeline(InvoiceStore store,
                           InvoiceAgentFactory agents,
                           PdfTextExtractor pdfExtractor,
                           AuditTrail audit,
                           ObjectMapper objectMapper) {
        this.store = store;
        this.agents = agents;
        this.pdfExtractor = pdfExtractor;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Async("pipelineExecutor")
    public void runIntakePipeline(UUID id) {
        try {
            // --- Stage 1: Ingest & Understanding ---------------------------
            transition(id, InvoiceStatus.EXTRACTING);
            InvoiceRecord record = store.findById(id).orElseThrow();
            String text = pdfExtractor.extractText(record.pdfBytes());

            String rawJson = agents.extractionAgent().run(text);
            InvoiceData data = parseInvoiceData(rawJson);
            store.update(id, r -> r.toBuilder().extractedData(data).build());
            audit.record(id, "extraction-agent", "DATA_EXTRACTED",
                    "invoiceNumber=%s, confidence=%s".formatted(data.invoiceNumber(), data.confidence()));

            // --- Stage 2: Validation & Enrichment --------------------------
            transition(id, InvoiceStatus.VALIDATING);
            String reviewSummary = agents.validationAgent()
                    .run("Validate invoice with internal id: " + id);
            store.update(id, r -> r.toBuilder().reviewSummary(reviewSummary).build());

            // --- Stage 3: route to four-eye human review -------------------
            transition(id, InvoiceStatus.PENDING_REVIEW);
            audit.record(id, "review-router", "ROUTED_FOR_REVIEW", "Awaiting four-eye approval");
        } catch (Exception e) {
            log.error("Intake pipeline failed for invoice {}", id, e);
            store.update(id, r -> r.toBuilder()
                    .status(InvoiceStatus.FAILED)
                    .failureReason(e.getMessage())
                    .build());
            audit.record(id, "pipeline", "INTAKE_FAILED", String.valueOf(e.getMessage()));
        }
    }

    /** Runs after four-eye APPROVE (status already transitioned to POSTING). */
    @Async("pipelineExecutor")
    public void runPostingPipeline(UUID id) {
        try {
            String summary = agents.postingAgent()
                    .run("Post the approved invoice with internal id: " + id);
            audit.record(id, "posting-agent", "POSTING_PIPELINE_FINISHED",
                    summary.substring(0, Math.min(summary.length(), 300)));
        } catch (Exception e) {
            log.error("Posting pipeline failed for invoice {}", id, e);
            store.update(id, r -> r.toBuilder()
                    .status(InvoiceStatus.POSTING_FAILED)
                    .failureReason(e.getMessage())
                    .build());
            audit.record(id, "pipeline", "POSTING_FAILED", String.valueOf(e.getMessage()));
        }
    }

    /** Runs after four-eye REJECT. */
    @Async("pipelineExecutor")
    public void runRejectionNotification(UUID id, String reason) {
        try {
            agents.rejectionAgent().run(
                    "Invoice with internal id %s was rejected by the reviewer. Reason: %s"
                            .formatted(id, reason));
        } catch (Exception e) {
            log.error("Rejection notification failed for invoice {}", id, e);
            audit.record(id, "pipeline", "REJECTION_NOTIFY_FAILED", String.valueOf(e.getMessage()));
        }
    }

    private void transition(UUID id, InvoiceStatus to) {
        store.update(id, r -> r.toBuilder().status(to).build());
        audit.record(id, "pipeline", "STATUS_CHANGED", "-> " + to);
    }

    private InvoiceData parseInvoiceData(String raw) throws Exception {
        // Models occasionally wrap JSON in fences despite instructions — strip defensively.
        String json = raw.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        return objectMapper.readValue(json, InvoiceData.class);
    }
}
