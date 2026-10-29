package com.example.invoice.integrations;

import com.example.invoice.config.InvoiceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiHttpClient {
    private final InvoiceProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public OpenAiHttpClient(InvoiceProperties properties, HttpClient httpClient, ObjectMapper mapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    public String chat(String systemPrompt, String userPrompt) throws Exception {
        InvoiceProperties.OpenAi cfg = properties.ai().openai();
        if (cfg.apiKey().isBlank()) {
            return "{}";
        }

        Map<String, Object> body = Map.of(
                "model", cfg.model(),
                "temperature", 0.0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        String url = trimTrailingSlash(cfg.baseUrl()) + "/v1/chat/completions";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(cfg.timeoutSeconds()))
                .header("Authorization", "Bearer " + cfg.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            throw new IllegalStateException("OpenAI-compatible endpoint failed: HTTP " + response.statusCode() + " " + abbreviate(response.body(), 500));
        }
        JsonNode root = mapper.readTree(response.body());
        JsonNode choices = root.path("choices");
        return choices.isArray() && !choices.isEmpty()
                ? choices.get(0).path("message").path("content").asText("{}")
                : "{}";
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String abbreviate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
