package com.example.agentwatcher;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OpenAiVisionClient {
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    OpenAiVisionClient(String apiKey, String model, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.model = model;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    Map<String, Object> extract(Path filePath) throws IOException {
        String mime = URLConnection.guessContentTypeFromName(filePath.getFileName().toString());
        if (mime == null || mime.isBlank()) {
            mime = "image/jpeg";
        }
        String encoded = Base64.getEncoder().encodeToString(Files.readAllBytes(filePath));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", List.of(
                Map.of("role", "system", "content", ExtractionFields.SYSTEM_PROMPT),
                Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of(
                                        "type", "text",
                                        "text", "Extract the data points from this identity document."
                                ),
                                Map.of(
                                        "type", "image_url",
                                        "image_url", Map.of("url", "data:%s;base64,%s".formatted(mime, encoded))
                                )
                        )
                )
        ));
        request.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "id_document_extraction",
                        "schema", ExtractionFields.jsonSchema(),
                        "strict", true
                )
        ));

        String responseBody = restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(String.class);

        JsonNode response = objectMapper.readTree(responseBody);
        JsonNode content = response.path("choices").path(0).path("message").path("content");
        if (!content.isTextual()) {
            throw new IOException("OpenAI response did not contain message content");
        }
        return objectMapper.readValue(content.asText(), new TypeReference<Map<String, Object>>() {
        });
    }
}
