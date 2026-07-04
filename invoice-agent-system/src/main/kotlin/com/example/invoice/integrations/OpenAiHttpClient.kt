package com.example.invoice.integrations

import com.example.invoice.config.InvoiceProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class OpenAiHttpClient(
    private val properties: InvoiceProperties,
    private val httpClient: HttpClient,
    private val mapper: ObjectMapper
) {
    fun chat(systemPrompt: String, userPrompt: String): String {
        val cfg = properties.ai.openai
        if (cfg.apiKey.isBlank()) {
            return "{}"
        }
        val body = mapOf(
            "model" to cfg.model,
            "temperature" to 0.0,
            "response_format" to mapOf("type" to "json_object"),
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userPrompt)
            )
        )
        val url = cfg.baseUrl.trimEnd('/') + "/v1/chat/completions"
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(cfg.timeoutSeconds))
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("OpenAI-compatible endpoint failed: HTTP ${response.statusCode()} ${response.body().take(500)}")
        }
        val root = mapper.readTree(response.body())
        return root.path("choices").firstOrNull()?.path("message")?.path("content")?.asText() ?: "{}"
    }
}
