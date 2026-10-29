package com.example.invoice.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class ProcessingStatus { RECEIVED, EXTRACTED, VALIDATED, REVIEW_REQUIRED, POSTED, CONTROL_PACK_CREATED, CLIENT_NOTIFIED, FAILED }
enum class ReviewDecision { APPROVED, REJECTED, NEEDS_MANUAL_REVIEW }

data class InvoiceProcessRequest(
    val source: String = "REST_JSON",
    val fileName: String? = null,
    val contentType: String? = null,
    val pdfBase64: String? = null,
    val manualText: String? = null,
    val clientCallbackUrl: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class InvoiceProcessResponse(
    val invoiceId: String,
    val status: ProcessingStatus,
    val extracted: ExtractedInvoice? = null,
    val validation: ValidationResult? = null,
    val review: ReviewResult? = null,
    val geneva: GenevaPostResult? = null,
    val navControlPack: NavControlPack? = null,
    val clientMessage: ClientNotificationResult? = null,
    val errors: List<String> = emptyList(),
    val audit: List<AuditEvent> = emptyList()
)

data class InvoiceContext(
    val invoiceId: String = UUID.randomUUID().toString(),
    val source: String,
    val fileName: String?,
    val contentType: String?,
    val pdfBytes: ByteArray?,
    val manualText: String?,
    val clientCallbackUrl: String?,
    val metadata: Map<String, String>,
    val startedAt: Instant = Instant.now(),
    val audit: MutableList<AuditEvent> = mutableListOf()
) {
    fun record(agent: String, action: String, outcome: String) {
        audit += AuditEvent(Instant.now(), agent, action, outcome)
    }
}

data class AuditEvent(
    val timestamp: Instant,
    val agent: String,
    val action: String,
    val outcome: String
)

data class ExtractedInvoice(
    val invoiceNumber: String?,
    val vendorName: String?,
    val vendorId: String?,
    val clientName: String?,
    val currency: String?,
    val grossAmount: BigDecimal?,
    val netAmount: BigDecimal?,
    val taxAmount: BigDecimal?,
    val invoiceDate: LocalDate?,
    val dueDate: LocalDate?,
    val paymentReference: String?,
    val lineItems: List<InvoiceLineItem> = emptyList(),
    val confidence: Double = 0.0,
    val rawModelJson: String? = null
)

data class InvoiceLineItem(
    val description: String,
    val amount: BigDecimal?,
    val glAccount: String? = null,
    val fund: String? = null
)

data class ValidationResult(
    val valid: Boolean,
    val checks: List<ValidationCheck>,
    val enrichedFields: Map<String, String> = emptyMap()
)

data class ValidationCheck(
    val name: String,
    val passed: Boolean,
    val message: String
)

data class ReviewResult(
    val decision: ReviewDecision,
    val reviewersRequired: Int = 2,
    val reviewersCaptured: Int = 0,
    val comments: List<String> = emptyList()
)

data class GenevaPostResult(
    val posted: Boolean,
    val downstreamReference: String? = null,
    val message: String? = null,
    val requestPayload: Map<String, Any?> = emptyMap()
)

data class NavControlPack(
    val controlPackId: String,
    val invoiceId: String,
    val createdAt: Instant,
    val status: String,
    val checksSummary: Map<String, String>,
    val paymentConfirmation: String?
)

data class ClientNotificationResult(
    val sent: Boolean,
    val outcome: String,
    val channel: String,
    val message: String
)
