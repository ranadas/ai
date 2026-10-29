package com.example.invoice.agent;

import org.springframework.stereotype.Component;

/**
 * Koog integration seam.
 *
 * The build includes ai.koog:koog-agents:1.0.0. The Spring Boot 4.0.7 application is Java 21 based,
 * while Koog can be introduced behind InvoiceAgent<I, O> implementations for tool-enabled agents,
 * memory, tracing, and agent lifecycle management.
 */
@Component
public class KoogAgentRuntime {
    public String agentRuntimeName() {
        return "Koog 1.0.0 runtime seam for Java 21 invoice agents";
    }
}
