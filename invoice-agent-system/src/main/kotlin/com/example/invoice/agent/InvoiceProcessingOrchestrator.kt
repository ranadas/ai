package com.example.invoice.agent

import com.example.invoice.domain.*
import com.example.invoice.store.InvoiceMemoryStore
import org.springframework.stereotype.Service
import java.util.Base64

@Service
class InvoiceProcessingOrchestrator(
    private val ingestAgent: InvoiceIngestAndUnderstandAgent,
    private val validationAgent: DataValidationAndEnrichmentAgent,
    private val fourEyeReviewAgent: FourEyeReviewAgent,
    private val genevaAgent: GenevaPostAndIntegrationAgent,
    private val navControlPackAgent: NavControlPackAgent,
    private val clientNotificationAgent: ClientNotificationAgent,
    private val memoryStore: InvoiceMemoryStore
) {
    fun process(request: InvoiceProcessRequest): InvoiceProcessResponse {
        val ctx = InvoiceContext(
            source = request.source,
            fileName = request.fileName,
            contentType = request.contentType,
            pdfBytes = request.pdfBase64?.let { Base64.getDecoder().decode(it) },
            manualText = request.manualText,
            clientCallbackUrl = request.clientCallbackUrl,
            metadata = request.metadata
        )
        ctx.record("AI Agent Orchestration Layer", "start", "Received invoice from ${request.source}")
        return try {
            val extracted = ingestAgent.run(request, ctx)
            val validation = validationAgent.run(extracted, ctx)
            val review = fourEyeReviewAgent.run(extracted to validation, ctx)
            val geneva = genevaAgent.run(Triple(extracted, validation, review), ctx)
            val nav = navControlPackAgent.run(validation to geneva, ctx)
            val preliminary = InvoiceProcessResponse(
                invoiceId = ctx.invoiceId,
                status = when {
                    geneva.posted -> ProcessingStatus.CONTROL_PACK_CREATED
                    review.decision == ReviewDecision.NEEDS_MANUAL_REVIEW -> ProcessingStatus.REVIEW_REQUIRED
                    else -> ProcessingStatus.FAILED
                },
                extracted = extracted,
                validation = validation,
                review = review,
                geneva = geneva,
                navControlPack = nav,
                errors = listOfNotNull(geneva.message?.takeIf { !geneva.posted })
            )
            val notification = clientNotificationAgent.run(preliminary, ctx)
            val final = preliminary.copy(
                status = if (notification.outcome == "SUCCESS") ProcessingStatus.CLIENT_NOTIFIED else preliminary.status,
                clientMessage = notification,
                audit = ctx.audit.toList()
            )
            memoryStore.save(final)
        } catch (ex: Exception) {
            ctx.record("AI Agent Orchestration Layer", "fail", ex.message ?: ex::class.simpleName.orEmpty())
            memoryStore.save(InvoiceProcessResponse(
                invoiceId = ctx.invoiceId,
                status = ProcessingStatus.FAILED,
                errors = listOf(ex.message ?: "Unknown processing failure"),
                audit = ctx.audit.toList()
            ))
        }
    }
}
