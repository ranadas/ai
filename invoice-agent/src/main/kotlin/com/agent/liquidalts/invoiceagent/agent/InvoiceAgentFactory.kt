package com.agent.liquidalts.invoiceagent.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import com.agent.liquidalts.invoiceagent.agent.tools.PostingTools
import com.agent.liquidalts.invoiceagent.agent.tools.ValidationTools
import com.agent.liquidalts.invoiceagent.config.InvoiceAgentProperties
import org.springframework.stereotype.Component

/**
 * Builds one Koog [AIAgent] per pipeline stage.
 *
 * Agents are cheap, single-use objects (per Koog's design); the expensive,
 * shared pieces — the [PromptExecutor] auto-configured by the
 * koog-spring-ai starter, and the Spring-managed tool sets — are injected once.
 *
 * Deliberate architecture choice: the *sequence* of stages is deterministic
 * Kotlin (see InvoiceProcessingService); the LLM only reasons *within* a
 * stage. In a regulated workflow the control flow must be code, not prompt.
 */
@Component
class InvoiceAgentFactory(
    private val promptExecutor: PromptExecutor,
    private val model: LLModel,
    private val validationTools: ValidationTools,
    private val postingTools: PostingTools,
    private val props: InvoiceAgentProperties,
) {

    /** Agent 1 — Invoice Ingest & Understanding. Pure structured extraction, no tools. */
    fun extractionAgent(): AIAgent<String, String> = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = model,
        systemPrompt = AgentPrompts.EXTRACTION,
        maxIterations = 3,
    )

    /** Agent 2 — Data Validation & Enrichment. */
    fun validationAgent(): AIAgent<String, String> = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = model,
        systemPrompt = AgentPrompts.VALIDATION,
        toolRegistry = ToolRegistry { tools(validationTools.asTools()) },
        maxIterations = props.llm.maxAgentIterations,
    )

    /** Agents 4 & 5 — Post & Integration, Control Pack & Notification. */
    fun postingAgent(): AIAgent<String, String> = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = model,
        systemPrompt = AgentPrompts.POSTING,
        toolRegistry = ToolRegistry { tools(postingTools.asTools()) },
        maxIterations = props.llm.maxAgentIterations,
    )

    /** Rejection path — notification only. */
    fun rejectionAgent(): AIAgent<String, String> = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = model,
        systemPrompt = AgentPrompts.REJECTION_NOTIFICATION,
        toolRegistry = ToolRegistry { tools(postingTools.asTools()) },
        maxIterations = 5,
    )
}
