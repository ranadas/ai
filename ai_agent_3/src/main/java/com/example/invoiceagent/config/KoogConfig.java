package com.example.invoiceagent.config;

import ai.koog.http.client.HttpClientFactoryResolver;
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig;
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings;
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.executor.model.PromptExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KoogConfig {

    @Bean
    PromptExecutor promptExecutor(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.base-url}") String baseUrl
    ) {
        OpenAIClientSettings settings = new OpenAIClientSettings(
                normalizeBaseUrl(baseUrl),
                new ConnectionTimeoutConfig(),
                "v1/chat/completions",
                "v1/responses",
                "v1/embeddings",
                "v1/moderations",
                "v1/models"
        );
        OpenAILLMClient openAILLMClient = new OpenAILLMClient(
                apiKey,
                settings,
                HttpClientFactoryResolver.INSTANCE.resolve()
        );
        return new MultiLLMPromptExecutor(openAILLMClient);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.openai.com";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
