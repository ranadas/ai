package com.example.invoice.integrations

import com.example.invoice.config.InvoiceProperties
import com.example.invoice.domain.ClientNotificationResult
import com.example.invoice.domain.InvoiceProcessResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Component
class ClientCallbackClient(
    private val properties: InvoiceProperties,
    private val httpClient: HttpClient,
    private val mapper: ObjectMapper
) {
    fun send(response: InvoiceProcessResponse, overrideUrl: String?): ClientNotificationResult {
        val target = overrideUrl ?: properties.clientCallback.baseUrl
        val outcome = if (response.errors.isEmpty() && response.geneva?.posted == true) "SUCCESS" else "FAILURE"
        val message = if (outcome == "SUCCESS") {
            "Payment confirmed for invoice ${response.extracted?.invoiceNumber ?: response.invoiceId}."
        } else {
            "Payment rejected or requires action for invoice ${response.extracted?.invoiceNumber ?: response.invoiceId}: ${response.errors.joinToString()}"
        }
        if (!properties.clientCallback.enabled && overrideUrl.isNullOrBlank()) {
            return ClientNotificationResult(false, outcome, "DRY_RUN", message)
        }
        val request = HttpRequest.newBuilder(URI.create(target.trimEnd('/') + "/api/payment-status"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${properties.clientCallback.apiKey}")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(mapOf(
                "invoiceId" to response.invoiceId,
                "outcome" to outcome,
                "message" to message
            ))))
            .build()
        return try {
            val http = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            ClientNotificationResult(http.statusCode() in 200..299, outcome, "HTTP", message)
        } catch (ex: Exception) {
            ClientNotificationResult(false, outcome, "HTTP", "Client callback failed: ${ex.message}")
        }
    }
}
