package com.example.invoiceagent.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "invoice-agent")
public record InvoiceAgentProperties(
        long maxPdfBytes,
        Geneva geneva,
        Client client,
        NavControlPack navControlPack
) {
    public record Geneva(String baseUrl, String updatePath, boolean dryRun) {
    }

    public record Client(String defaultCallbackUrl, boolean dryRun) {
    }

    public record NavControlPack(Path storageDir) {
    }
}
