package com.example.invoice.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "invoice")
data class InvoiceProperties(
    val ai: Ai = Ai(),
    val geneva: Geneva = Geneva(),
    val clientCallback: ClientCallback = ClientCallback()
) {
    data class Ai(
        val mode: String = "multi",
        val openai: OpenAi = OpenAi()
    )

    data class OpenAi(
        val apiKey: String = "",
        @field:NotBlank val baseUrl: String = "https://api.openai.com",
        val model: String = "gpt-4o-mini",
        val timeoutSeconds: Long = 60
    )

    data class Geneva(
        @field:NotBlank val baseUrl: String = "http://localhost:9091",
        val apiKey: String = "",
        val timeoutSeconds: Long = 30
    )

    data class ClientCallback(
        val enabled: Boolean = false,
        val baseUrl: String = "http://localhost:9092",
        val apiKey: String = ""
    )
}
