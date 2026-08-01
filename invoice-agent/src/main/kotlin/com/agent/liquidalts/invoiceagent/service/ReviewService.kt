package com.agent.liquidalts.invoiceagent.service

import com.agent.liquidalts.invoiceagent.audit.AuditTrail
import com.agent.liquidalts.invoiceagent.config.InvoiceAgentProperties
import com.agent.liquidalts.invoiceagent.domain.Decision
import com.agent.liquidalts.invoiceagent.domain.InvoiceRecord
import com.agent.liquidalts.invoiceagent.domain.InvoiceStatus
import com.agent.liquidalts.invoiceagent.domain.ReviewDecision
import com.agent.liquidalts.invoiceagent.store.InvoiceStore
import org.springframework.stereotype.Service
import java.util.UUID

class InvoiceNotFoundException(val invoiceId: UUID) : RuntimeException("Invoice $invoiceId not found")
class IllegalReviewStateException(message: String) : RuntimeException(message)
class SegregationOfDutiesException(message: String) : RuntimeException(message)

/**
 * The human-in-the-loop four-eye gate (agent 3 on the diagram captures the
 * approval; this service enforces it). Deliberately NOT an LLM concern:
 * approval authority, segregation of duties, and state transitions are
 * enforced in code before any agent is allowed to touch Geneva.
 */
@Service
class ReviewService(
    private val store: InvoiceStore,
    private val processing: InvoiceProcessingService,
    private val audit: AuditTrail,
    private val props: InvoiceAgentProperties,
) {

    fun decide(invoiceId: UUID, reviewer: String, decision: Decision, comment: String?): InvoiceRecord {
        val record = store.findById(invoiceId) ?: throw InvoiceNotFoundException(invoiceId)

        if (record.status != InvoiceStatus.PENDING_REVIEW) {
            throw IllegalReviewStateException(
                "Invoice $invoiceId is not awaiting review (status=${record.status})"
            )
        }
        if (props.review.enforceSegregationOfDuties && reviewer.equals(record.submittedBy, ignoreCase = true)) {
            audit.record(invoiceId, reviewer, "SOD_VIOLATION_BLOCKED", "Submitter attempted self-review")
            throw SegregationOfDutiesException(
                "Four-eye principle: reviewer must differ from submitter '${record.submittedBy}'"
            )
        }

        val reviewed = store.update(invoiceId) {
            it.copy(reviewDecision = ReviewDecision(reviewer, decision, comment))
        }
        audit.record(invoiceId, reviewer, "REVIEW_DECISION", "$decision${comment?.let { " — $it" } ?: ""}")

        return when (decision) {
            Decision.APPROVE -> {
                processing.startPostingPipeline(invoiceId)   // transitions to POSTING, runs async
                store.findById(invoiceId) ?: reviewed
            }
            Decision.REJECT -> {
                processing.startRejectionNotification(invoiceId, comment ?: "Rejected by reviewer $reviewer")
                store.update(invoiceId) {
                    it.copy(status = InvoiceStatus.REJECTED, failureReason = comment ?: "Rejected by reviewer")
                }
            }
        }
    }
}
