package com.example.invoice.agent

import com.example.invoice.domain.*
import com.example.invoice.integrations.ClientCallbackClient
import com.example.invoice.integrations.GenevaClient
import com.example.invoice.integrations.OpenAiHttpClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

interface InvoiceAgent<I, O> {
    val name: String
    fun run(input: I, ctx: InvoiceContext): O
}

@Component
class InvoiceIngestAndUnderstandAgent(
    private val openAi: OpenAiHttpClient,
    private val mapper: ObjectMapper
) : InvoiceAgent<InvoiceProcessRequest, ExtractedInvoice> {
    override val name = "1. Invoice Ingest & Understanding Agent"

    override fun run(input: InvoiceProcessRequest, ctx: InvoiceContext): ExtractedInvoice {
        ctx.record(name, "extract", "Started invoice extraction")
        val text = input.manualText ?: "PDF base64 size=${input.pdfBase64?.length ?: 0}; filename=${input.fileName}"
        val system = """
            You extract invoice data. Return strict JSON with keys: invoiceNumber, vendorName, vendorId,
            clientName, currency, grossAmount, netAmount, taxAmount, invoiceDate, dueDate,
            paymentReference, lineItems, confidence. Dates must be yyyy-MM-dd. Amounts numeric.
        """.trimIndent()
        val json = try { openAi.chat(system, text) } catch (ex: Exception) { "{}" }
        return parseOrFallback(json, text).also {
            ctx.record(name, "extract", "Extracted invoice=${it.invoiceNumber ?: "UNKNOWN"}, confidence=${it.confidence}")
        }
    }

    private fun parseOrFallback(json: String, text: String): ExtractedInvoice = try {
        val node = mapper.readTree(json)
        ExtractedInvoice(
            invoiceNumber = node.path("invoiceNumber").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
            vendorName = node.path("vendorName").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
            vendorId = node.path("vendorId").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
            clientName = node.path("clientName").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
            currency = node.path("currency").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
            grossAmount = node.path("grossAmount").takeIf { it.isNumber || it.isTextual }?.asText()?.toBigDecimalOrNull(),
            netAmount = node.path("netAmount").takeIf { it.isNumber || it.isTextual }?.asText()?.toBigDecimalOrNull(),
            taxAmount = node.path("taxAmount").takeIf { it.isNumber || it.isTextual }?.asText()?.toBigDecimalOrNull(),
            invoiceDate = node.path("invoiceDate").asText(null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            dueDate = node.path("dueDate").asText(null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            paymentReference = node.path("paymentReference").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
            confidence = node.path("confidence").asDouble(0.55),
            rawModelJson = json
        )
    } catch (_: Exception) {
        ExtractedInvoice(
            invoiceNumber = Regex("(?i)invoice\s*(no|number|#)?[:\s-]+([A-Z0-9-]+)").find(text)?.groupValues?.last(),
            vendorName = Regex("(?i)vendor[:\s-]+([^\n]+)").find(text)?.groupValues?.getOrNull(1)?.trim(),
            vendorId = null,
            clientName = Regex("(?i)client[:\s-]+([^\n]+)").find(text)?.groupValues?.getOrNull(1)?.trim(),
            currency = Regex("\b(USD|EUR|GBP|INR|CHF)\b").find(text)?.value,
            grossAmount = Regex("(?i)(total|gross)[:\s]+([0-9,.]+)").find(text)?.groupValues?.getOrNull(2)?.replace(",", "")?.toBigDecimalOrNull(),
            netAmount = null,
            taxAmount = null,
            invoiceDate = null,
            dueDate = null,
            paymentReference = null,
            confidence = 0.35,
            rawModelJson = json
        )
    }
}

@Component
class DataValidationAndEnrichmentAgent : InvoiceAgent<ExtractedInvoice, ValidationResult> {
    override val name = "2. Data Validation & Enrichment Agent"

    override fun run(input: ExtractedInvoice, ctx: InvoiceContext): ValidationResult {
        val checks = listOf(
            ValidationCheck("Invoice number present", !input.invoiceNumber.isNullOrBlank(), "Invoice number is mandatory"),
            ValidationCheck("Vendor present", !input.vendorName.isNullOrBlank() || !input.vendorId.isNullOrBlank(), "Vendor name or vendor id is mandatory"),
            ValidationCheck("Amount positive", (input.grossAmount ?: BigDecimal.ZERO) > BigDecimal.ZERO, "Gross amount must be positive"),
            ValidationCheck("Currency present", !input.currency.isNullOrBlank(), "Currency is mandatory"),
            ValidationCheck("Extraction confidence", input.confidence >= 0.70, "Confidence should be >= 0.70 for straight-through processing")
        )
        val result = ValidationResult(
            valid = checks.all { it.passed },
            checks = checks,
            enrichedFields = mapOf(
                "vendorMasterStatus" to if (!input.vendorName.isNullOrBlank() || !input.vendorId.isNullOrBlank()) "MATCHED_OR_PENDING_MATCH" else "MISSING",
                "poGrContractStatus" to "NOT_CONFIGURED_DEMO"
            )
        )
        ctx.record(name, "validate", "valid=${result.valid}; failed=${checks.count { !it.passed }}")
        return result
    }
}

@Component
class FourEyeReviewAgent : InvoiceAgent<Pair<ExtractedInvoice, ValidationResult>, ReviewResult> {
    override val name = "3. Four Eye Review Agent"

    override fun run(input: Pair<ExtractedInvoice, ValidationResult>, ctx: InvoiceContext): ReviewResult {
        val (invoice, validation) = input
        val requiresManual = !validation.valid || invoice.confidence < 0.85 || (invoice.grossAmount ?: BigDecimal.ZERO) > BigDecimal("100000")
        val result = if (requiresManual) {
            ReviewResult(ReviewDecision.NEEDS_MANUAL_REVIEW, 2, 0, listOf("Four-eye approval required before Geneva posting."))
        } else {
            ReviewResult(ReviewDecision.APPROVED, 2, 2, listOf("Auto-approved by policy for high confidence, low-risk invoice."))
        }
        ctx.record(name, "review", "decision=${result.decision}")
        return result
    }
}

@Component
class GenevaPostAndIntegrationAgent(private val genevaClient: GenevaClient) : InvoiceAgent<Triple<ExtractedInvoice, ValidationResult, ReviewResult>, GenevaPostResult> {
    override val name = "4. Post & Integration Agent"

    override fun run(input: Triple<ExtractedInvoice, ValidationResult, ReviewResult>, ctx: InvoiceContext): GenevaPostResult {
        val (invoice, validation, review) = input
        if (review.decision != ReviewDecision.APPROVED) {
            val result = GenevaPostResult(false, null, "Skipped: review decision ${review.decision}")
            ctx.record(name, "post", result.message ?: "Skipped")
            return result
        }
        val result = genevaClient.postInvoice(ctx.invoiceId, invoice, validation)
        ctx.record(name, "post", "posted=${result.posted}; reference=${result.downstreamReference}")
        return result
    }
}

@Component
class NavControlPackAgent : InvoiceAgent<Pair<ValidationResult, GenevaPostResult>, NavControlPack> {
    override val name = "5a. NAV Control Pack Agent"

    override fun run(input: Pair<ValidationResult, GenevaPostResult>, ctx: InvoiceContext): NavControlPack {
        val (validation, geneva) = input
        val pack = NavControlPack(
            controlPackId = "NAV-${UUID.randomUUID()}",
            invoiceId = ctx.invoiceId,
            createdAt = Instant.now(),
            status = if (geneva.posted) "READY" else "BLOCKED",
            checksSummary = validation.checks.associate { it.name to if (it.passed) "PASS" else "FAIL: ${it.message}" },
            paymentConfirmation = geneva.downstreamReference
        )
        ctx.record(name, "control-pack", "status=${pack.status}; id=${pack.controlPackId}")
        return pack
    }
}

@Component
class ClientNotificationAgent(private val clientCallbackClient: ClientCallbackClient) : InvoiceAgent<InvoiceProcessResponse, ClientNotificationResult> {
    override val name = "5b. Control Pack & Notification Agent"

    override fun run(input: InvoiceProcessResponse, ctx: InvoiceContext): ClientNotificationResult {
        val result = clientCallbackClient.send(input, ctx.clientCallbackUrl)
        ctx.record(name, "notify", "sent=${result.sent}; outcome=${result.outcome}")
        return result
    }
}
