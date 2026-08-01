package com.agent.liquidalts.invoiceagent.config;

import ai.koog.prompt.llm.LLMCapability;
import ai.koog.prompt.llm.LLMProvider;
import ai.koog.prompt.llm.LLModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.Executor;

@Configuration
public class AppConfig {

    /**
     * Koog agents still need an {@link LLModel} descriptor even when the actual
     * transport is delegated to Spring AI. The id here should match the model
     * configured under {@code spring.ai.openai.chat.options.model}.
     */
    @Bean
    LLModel agentModel(InvoiceAgentProperties props) {
        return new LLModel(
                LLMProvider.OpenAI.INSTANCE,
                props.llm().model(),
                List.of(
                        LLMCapability.Completion.INSTANCE,
                        LLMCapability.Tools.INSTANCE,
                        LLMCapability.Temperature.INSTANCE
                ),
                props.llm().contextLength()
        );
    }

    /**
     * Dedicated pool for the async invoice pipeline (agent runs are
     * long-lived, I/O-bound calls — keep them off the servlet threads).
     * Bounded queue: under sustained overload we would rather fail fast
     * at submission than build an invisible backlog.
     */
    @Bean(name = "pipelineExecutor")
    Executor pipelineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("invoice-pipeline-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}
