package com.agent.liquidalts.invoiceagent.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.prompt.executor.model.PromptExecutor;
import ai.koog.prompt.llm.LLModel;
import com.agent.liquidalts.invoiceagent.agent.tools.PostingTools;
import com.agent.liquidalts.invoiceagent.agent.tools.ValidationTools;
import com.agent.liquidalts.invoiceagent.config.InvoiceAgentProperties;
import org.springframework.stereotype.Component;

/**
 * Builds one Koog {@link AIAgent} per pipeline stage using Koog's Java
 * builder API. Agents are cheap, single-use objects (per Koog's design);
 * the expensive, shared pieces — the {@link PromptExecutor} auto-configured
 * by the koog-spring-ai starter, and the Spring-managed tool sets — are
 * injected once.
 *
 * Deliberate architecture choice: the <em>sequence</em> of stages is
 * deterministic Java (see InvoicePipeline); the LLM only reasons
 * <em>within</em> a stage. In a regulated workflow the control flow must
 * be code, not prompt.
 */
@Component
public class InvoiceAgentFactory {

    private final PromptExecutor promptExecutor;
    private final LLModel model;
    private final ToolRegistry validationRegistry;
    private final ToolRegistry postingRegistry;
    private final int maxIterations;

    public InvoiceAgentFactory(PromptExecutor promptExecutor,
                               LLModel model,
                               ValidationTools validationTools,
                               PostingTools postingTools,
                               InvoiceAgentProperties props) {
        this.promptExecutor = promptExecutor;
        this.model = model;
        this.validationRegistry = ToolRegistry.builder().tools(validationTools).build();
        this.postingRegistry = ToolRegistry.builder().tools(postingTools).build();
        this.maxIterations = props.llm().maxAgentIterations();
    }

    /** Agent 1 — Invoice Ingest & Understanding. Pure structured extraction, no tools. */
    public AIAgent<String, String> extractionAgent() {
        return AIAgent.builder()
                .promptExecutor(promptExecutor)
                .llmModel(model)
                .systemPrompt(AgentPrompts.EXTRACTION)
                .maxIterations(3)
                .build();
    }

    /** Agent 2 — Data Validation & Enrichment. */
    public AIAgent<String, String> validationAgent() {
        return AIAgent.builder()
                .promptExecutor(promptExecutor)
                .llmModel(model)
                .systemPrompt(AgentPrompts.VALIDATION)
                .toolRegistry(validationRegistry)
                .maxIterations(maxIterations)
                .build();
    }

    /** Agents 4 & 5 — Post & Integration, Control Pack & Notification. */
    public AIAgent<String, String> postingAgent() {
        return AIAgent.builder()
                .promptExecutor(promptExecutor)
                .llmModel(model)
                .systemPrompt(AgentPrompts.POSTING)
                .toolRegistry(postingRegistry)
                .maxIterations(maxIterations)
                .build();
    }

    /** Rejection path — notification only. */
    public AIAgent<String, String> rejectionAgent() {
        return AIAgent.builder()
                .promptExecutor(promptExecutor)
                .llmModel(model)
                .systemPrompt(AgentPrompts.REJECTION_NOTIFICATION)
                .toolRegistry(postingRegistry)
                .maxIterations(5)
                .build();
    }
}
