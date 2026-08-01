package com.example.invoice.agent

import org.springframework.stereotype.Component

/**
 * Koog integration seam.
 *
 * The build includes ai.koog:koog-agents:1.0.0. Keep the rest of the Spring application
 * behind the InvoiceAgent<I, O> contract so each processing step can be backed by:
 *  - a Koog AIAgent with tools,
 *  - a direct Spring AI ChatClient call,
 *  - deterministic business logic, or
 *  - a human review workbench adapter.
 *
 * In production, implement each InvoiceAgent with a Koog tool-enabled agent and keep this
 * class as the factory/registry for agent lifecycle, tracing, persistence, and memory.
 */
@Component
class KoogAgentRuntime {
    fun agentRuntimeName(): String = "Koog 1.0.0 runtime seam for invoice agents"
}
