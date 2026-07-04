package com.agent.liquidalts.invoiceagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * All application-owned configuration under a single, typed root.
 * LLM connectivity itself (api-key / base-url / model) lives under
 * {@code spring.ai.openai.*} and is owned by Spring AI auto-configuration.
 */
@ConfigurationProperties(prefix = "invoice-agent")
public record InvoiceAgentProperties(
        Llm llm,
        Review review,
        Geneva geneva,
        Notification notification
) {
    public record Llm(String model, long contextLength, int maxAgentIterations) {}

    /** Four-eye principle: the reviewer must not be the submitter. */
    public record Review(boolean enforceSegregationOfDuties) {}

    public record Geneva(String baseUrl, String apiKey, Duration connectTimeout, Duration readTimeout) {}

    public record Notification(String clientEndpoint) {}
}
