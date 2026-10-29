package com.agent.liquidalts.invoiceagent.api

import com.agent.liquidalts.invoiceagent.domain.Decision
import com.agent.liquidalts.invoiceagent.domain.Finding
import com.agent.liquidalts.invoiceagent.domain.InvoiceData
import com.agent.liquidalts.invoiceagent.domain.InvoiceRecord
import com.agent.liquidalts.invoiceagent.domain.InvoiceStatus
import com.agent.liquidalts.invoiceagent.domain.ReviewDecision
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class SubmissionResponse(
    val invoiceId: UUID,
    val status: InvoiceStatus,
    val duplicate: Boolean,
)

data class ReviewRequest(
    @field:NotBlank val reviewer: String,
    @field:NotNull val decision: Decision,
    val comment: String? = null,
)

data class InvoiceView(
    val invoiceId: UUID,
    val status: InvoiceStatus,
    val fileName: String,
    val submittedBy: String,
    val extractedData: InvoiceData?,
    val validationPassed: Boolean?,
    val findings: List<Finding>,
    val reviewSummary: String?,
    val reviewDecision: ReviewDecision?,
    val genevaReference: String?,
    val failureReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(r: InvoiceRecord) = InvoiceView(
            invoiceId = r.id,
            status = r.status,
            fileName = r.fileName,
            submittedBy = r.submittedBy,
            extractedData = r.extractedData,
            validationPassed = r.validationReport?.passed,
            findings = r.validationReport?.findings ?: emptyList(),
            reviewSummary = r.reviewSummary,
            reviewDecision = r.reviewDecision,
            genevaReference = r.genevaReference,
            failureReason = r.failureReason,
            createdAt = r.createdAt,
            updatedAt = r.updatedAt,
        )
    }
}
