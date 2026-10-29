package com.example.invoiceagent.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.invoiceagent.api.FourEyeReview;
import com.example.invoiceagent.api.InvoiceExtraction;
import com.example.invoiceagent.api.NavControlPackResult;
import com.example.invoiceagent.config.InvoiceAgentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NavControlPackService {

    private final InvoiceAgentProperties properties;
    private final ObjectMapper objectMapper;

    public NavControlPackResult create(UUID processId, String clientId, InvoiceExtraction extraction, FourEyeReview review) {
        try {
            Path storageDir = properties.navControlPack().storageDir();
            Files.createDirectories(storageDir);
            Path output = storageDir.resolve(processId + "-nav-control-pack.json");
            NavControlPack pack = new NavControlPack(processId, clientId, extraction, review, Instant.now());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), pack);
            return new NavControlPackResult(true, output.toString(), "NAV control pack created");
        } catch (IOException ex) {
            return new NavControlPackResult(false, null, "Could not create NAV control pack: " + ex.getMessage());
        }
    }

    record NavControlPack(
            UUID processId,
            String clientId,
            InvoiceExtraction extraction,
            FourEyeReview review,
            Instant createdAt
    ) {
    }
}
