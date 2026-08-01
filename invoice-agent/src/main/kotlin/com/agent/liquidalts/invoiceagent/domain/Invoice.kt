package com.agent.liquidalts.invoiceagent.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Lifecycle of an invoice through the agent pipeline.
 * Mirrors the five agents on the architecture diagram, with an explicit
 * human-in-the-loop gate at PENDING_REVIEW.
 */
enum class InvoiceStatus {
    RECEIVED,        // 1. ingested, PDF stored
    EXTRACTING,      // 1. extraction agent running
    VALIDATING,      // 2. validation & enrichment agent running
    PENDING_REVIEW,  // 3. waiting for four-eye human approval
    REJECTED,        // 3. reviewer rejected — terminal
    POSTING,         // 4. posting agent pushing to Geneva
    POSTING_FAILED,  // 4. Geneva push failed — terminal (client notified)
    COMPLETED,       // 5. posted, control pack created, client notified
    FAILED,          // pipeline error before review — terminal
}

enum class Decision { APPROVE, REJECT }

data class LineItem(
    val description: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val amount: BigDecimal,
)

/** Structured output of the extraction agent. */
data class InvoiceData(
    val invoiceNumber: String,
    val vendorName: String,
    val vendorId: String? = null,
    val poNumber: String? = null,
    val invoiceDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val currency: String,
    val netAmount: BigDecimal,
    val taxAmount: BigDecimal,
    val grossAmount: BigDecimal,
    val lineItems: List<LineItem> = emptyList(),
    /** Agent's self-assessed extraction confidence in [0.0, 1.0]. */
    val confidence: Double = 0.0,
)

enum class Severity { INFO, WARNING, BLOCKER }

data class Finding(
    val severity: Severity,
    val rule: String,
    val message: String,
)

/** Output of the validation & enrichment agent. */
data class ValidationReport(
    val passed: Boolean,
    val findings: List<Finding> = emptyList(),
    val enrichedVendorId: String? = null,
    val matchedPoNumber: String? = null,
)

data class ReviewDecision(
    val reviewer: String,
    val decision: Decision,
    val comment: String? = null,
    val decidedAt: Instant = Instant.now(),
)

/** Aggregate root tracked through the pipeline. */
data class InvoiceRecord(
    val id: UUID = UUID.randomUUID(),
    val idempotencyKey: String,
    val submittedBy: String,
    val fileName: String,
    val pdfBytes: ByteArray,
    val status: InvoiceStatus = InvoiceStatus.RECEIVED,
    val extractedData: InvoiceData? = null,
    val validationReport: ValidationReport? = null,
    val reviewSummary: String? = null,
    val reviewDecision: ReviewDecision? = null,
    val genevaReference: String? = null,
    val controlPack: String? = null,
    val failureReason: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    // ByteArray breaks data-class equality semantics; identity is the id.
    override fun equals(other: Any?) = other is InvoiceRecord && other.id == id
    override fun hashCode() = id.hashCode()
}
