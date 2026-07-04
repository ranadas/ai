package com.example.invoice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "invoice")
public record InvoiceProperties(
        Ai ai,
        Geneva geneva,
        ClientCallback clientCallback
) {
    public InvoiceProperties {
        ai = ai == null ? new Ai(null, null) : ai;
        geneva = geneva == null ? new Geneva(null, null, null) : geneva;
        clientCallback = clientCallback == null ? new ClientCallback(null, null, null) : clientCallback;
    }

    public record Ai(String mode, OpenAi openai) {
        public Ai {
            mode = mode == null || mode.isBlank() ? "multi" : mode;
            openai = openai == null ? new OpenAi(null, null, null, null) : openai;
        }
    }

    public record OpenAi(
            String apiKey,
            @NotBlank String baseUrl,
            String model,
            Long timeoutSeconds
    ) {
        public OpenAi {
            apiKey = apiKey == null ? "" : apiKey;
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com" : baseUrl;
            model = model == null || model.isBlank() ? "gpt-4o-mini" : model;
            timeoutSeconds = timeoutSeconds == null ? 60L : timeoutSeconds;
        }
    }

    public record Geneva(
            @NotBlank String baseUrl,
            String apiKey,
            Long timeoutSeconds
    ) {
        public Geneva {
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:9091" : baseUrl;
            apiKey = apiKey == null ? "" : apiKey;
            timeoutSeconds = timeoutSeconds == null ? 30L : timeoutSeconds;
        }
    }

    public record ClientCallback(
            Boolean enabled,
            String baseUrl,
            String apiKey
    ) {
        public ClientCallback {
            enabled = enabled != null && enabled;
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:9092" : baseUrl;
            apiKey = apiKey == null ? "" : apiKey;
        }
    }
}
