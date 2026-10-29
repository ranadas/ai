package com.agent.liquidalts.invoiceagent.config

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AppConfig(private val props: InvoiceAgentProperties) {

    /**
     * Koog agents still need an [LLModel] descriptor even when the actual
     * transport is delegated to Spring AI. The id here should match the
     * model configured under `spring.ai.openai.chat.options.model`.
     */
    @Bean
    fun agentModel(): LLModel = LLModel(
        provider = LLMProvider.OpenAI,
        id = props.llm.model,
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Tools,
            LLMCapability.Temperature,
        ),
        contextLength = props.llm.contextLength,
    )

    /**
     * Application-scoped coroutine scope for the async invoice pipeline.
     * SupervisorJob: one failing invoice must never cancel its siblings.
     */
    @Bean
    fun pipelineScope(): PipelineScope = PipelineScope()
}

class PipelineScope :
    CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default),
    DisposableBean {
    override fun destroy() = cancel("Application shutting down")
}
