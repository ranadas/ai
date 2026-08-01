package com.example.invoice.integrations

import com.example.invoice.config.InvoiceProperties
import com.example.invoice.domain.ExtractedInvoice
import com.example.invoice.domain.GenevaPostResult
import com.example.invoice.domain.ValidationResult
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

@Component
class GenevaClient(
    private val properties: InvoiceProperties,
    private val httpClient: HttpClient,
    private val mapper: ObjectMapper
) {
    fun postInvoice(invoiceId: String, extracted: ExtractedInvoice, validation: ValidationResult): GenevaPostResult {
        val payload = mapOf(
            "invoiceId" to invoiceId,
            "invoiceNumber" to extracted.invoiceNumber,
            "vendorId" to extracted.vendorId,
            "vendorName" to extracted.vendorName,
            "currency" to extracted.currency,
            "grossAmount" to extracted.grossAmount,
            "paymentReference" to extracted.paymentReference,
            "validated" to validation.valid,
            "enrichedFields" to validation.enrichedFields
        )
        val cfg = properties.geneva
        val request = HttpRequest.newBuilder(URI.create(cfg.baseUrl.trimEnd('/') + "/api/invoices"))
            .timeout(Duration.ofSeconds(cfg.timeoutSeconds))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .build()
        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            GenevaPostResult(
                posted = response.statusCode() in 200..299,
                downstreamReference = if (response.statusCode() in 200..299) "GEN-${UUID.randomUUID()}" else null,
                message = "HTTP ${response.statusCode()}",
                requestPayload = payload
            )
        } catch (ex: Exception) {
            GenevaPostResult(false, null, "Geneva call failed: ${ex.message}", payload)
        }
    }
}
