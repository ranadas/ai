package com.agent.liquidalts.invoiceagent.service

import com.agent.liquidalts.invoiceagent.agent.InvoiceAgentFactory
import com.agent.liquidalts.invoiceagent.audit.AuditTrail
import com.agent.liquidalts.invoiceagent.config.PipelineScope
import com.agent.liquidalts.invoiceagent.domain.InvoiceData
import com.agent.liquidalts.invoiceagent.domain.InvoiceRecord
import com.agent.liquidalts.invoiceagent.domain.InvoiceStatus
import com.agent.liquidalts.invoiceagent.extraction.PdfTextExtractor
import com.agent.liquidalts.invoiceagent.store.InvoiceStore
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Deterministic orchestrator around the Koog agents.
 *
 * Intake pipeline (async, kicked off at submission):
 *   RECEIVED -> EXTRACTING -> VALIDATING -> PENDING_REVIEW
 *
 * The human four-eye gate sits between the two pipelines. Nothing crosses
 * it without an explicit reviewer decision (see ReviewService).
 *
 * Posting pipeline (async, kicked off on APPROVE):
 *   POSTING -> COMPLETED | POSTING_FAILED
 */
@Service
class InvoiceProcessingService(
    private val store: InvoiceStore,
    private val agents: InvoiceAgentFactory,
    private val pdfExtractor: PdfTextExtractor,
    private val audit: AuditTrail,
    private val objectMapper: ObjectMapper,
    private val scope: PipelineScope,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Accepts a submission and returns immediately (202 semantics).
     * Idempotent on the Idempotency-Key: duplicates return the original record.
     */
    fun submit(idempotencyKey: String, submittedBy: String, fileName: String, pdfBytes: ByteArray): InvoiceRecord {
        store.findByIdempotencyKey(idempotencyKey)?.let {
            audit.record(it.id, submittedBy, "DUPLICATE_SUBMISSION", "Idempotency-Key already seen")
            return it
        }
        val record = store.save(
            InvoiceRecord(
                idempotencyKey = idempotencyKey,
                submittedBy = submittedBy,
                fileName = fileName,
                pdfBytes = pdfBytes,
            )
        )
        audit.record(record.id, submittedBy, "INVOICE_SUBMITTED", "file=$fileName, size=${pdfBytes.size}B")
        scope.launch { runIntakePipeline(record.id) }
        return record
    }

    internal suspend fun runIntakePipeline(id: UUID) {
        try {
            // --- Stage 1: Ingest & Understanding ---------------------------
            transition(id, InvoiceStatus.EXTRACTING)
            val record = store.findById(id) ?: return
            val text = pdfExtractor.extractText(record.pdfBytes)

            val rawJson = agents.extractionAgent().run(text)
            val data = parseInvoiceData(rawJson)
            store.update(id) { it.copy(extractedData = data) }
            audit.record(id, "extraction-agent", "DATA_EXTRACTED",
                "invoiceNumber=${data.invoiceNumber}, confidence=${data.confidence}")

            // --- Stage 2: Validation & Enrichment --------------------------
            transition(id, InvoiceStatus.VALIDATING)
            val reviewSummary = agents.validationAgent().run(
                "Validate invoice with internal id: $id"
            )
            store.update(id) { it.copy(reviewSummary = reviewSummary) }

            // --- Stage 3: route to four-eye human review -------------------
            transition(id, InvoiceStatus.PENDING_REVIEW)
            audit.record(id, "review-router", "ROUTED_FOR_REVIEW", "Awaiting four-eye approval")
        } catch (e: Exception) {
            log.error("Intake pipeline failed for invoice {}", id, e)
            store.update(id) { it.copy(status = InvoiceStatus.FAILED, failureReason = e.message) }
            audit.record(id, "pipeline", "INTAKE_FAILED", e.message ?: "unknown")
        }
    }

    /** Runs after four-eye APPROVE. */
    fun startPostingPipeline(id: UUID) {
        transition(id, InvoiceStatus.POSTING)
        scope.launch {
            try {
                val summary = agents.postingAgent().run(
                    "Post the approved invoice with internal id: $id"
                )
                audit.record(id, "posting-agent", "POSTING_PIPELINE_FINISHED", summary.take(300))
            } catch (e: Exception) {
                log.error("Posting pipeline failed for invoice {}", id, e)
                store.update(id) { it.copy(status = InvoiceStatus.POSTING_FAILED, failureReason = e.message) }
                audit.record(id, "pipeline", "POSTING_FAILED", e.message ?: "unknown")
            }
        }
    }

    /** Runs after four-eye REJECT. */
    fun startRejectionNotification(id: UUID, reason: String) {
        scope.launch {
            try {
                agents.rejectionAgent().run(
                    "Invoice with internal id $id was rejected by the reviewer. Reason: $reason"
                )
            } catch (e: Exception) {
                log.error("Rejection notification failed for invoice {}", id, e)
                audit.record(id, "pipeline", "REJECTION_NOTIFY_FAILED", e.message ?: "unknown")
            }
        }
    }

    private fun transition(id: UUID, to: InvoiceStatus) {
        store.update(id) { it.copy(status = to) }
        audit.record(id, "pipeline", "STATUS_CHANGED", "-> $to")
    }

    private fun parseInvoiceData(raw: String): InvoiceData {
        // Models occasionally wrap JSON in fences despite instructions — strip defensively.
        val json = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return objectMapper.readValue(json, InvoiceData::class.java)
    }
}
