package com.agent.liquidalts.invoiceagent.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * All application-owned configuration under a single, typed root.
 * LLM connectivity itself (api-key / base-url / model) lives under
 * `spring.ai.openai.*` and is owned by Spring AI auto-configuration.
 */
@ConfigurationProperties(prefix = "invoice-agent")
data class InvoiceAgentProperties(
    val llm: Llm,
    val review: Review,
    val geneva: Geneva,
    val notification: Notification,
) {
    data class Llm(
        val model: String,
        val contextLength: Long = 128_000,
        val maxAgentIterations: Int = 15,
    )

    data class Review(
        /** Four-eye principle: the reviewer must not be the submitter. */
        val enforceSegregationOfDuties: Boolean = true,
    )

    data class Geneva(
        val baseUrl: String,
        val apiKey: String,
        val connectTimeout: Duration = Duration.ofSeconds(5),
        val readTimeout: Duration = Duration.ofSeconds(30),
    )

    data class Notification(
        val clientEndpoint: String,
    )
}
