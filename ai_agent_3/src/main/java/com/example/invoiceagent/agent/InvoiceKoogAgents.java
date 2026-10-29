package com.example.invoiceagent.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.model.PromptExecutor;

import org.springframework.stereotype.Service;

import com.example.invoiceagent.api.FourEyeReview;
import com.example.invoiceagent.api.InvoiceExtraction;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class InvoiceKoogAgents {

    private static final String EXTRACTION_SYSTEM_PROMPT = """
            You are an invoice operations specialist.
            Extract payable invoice facts from OCR/PDF text.
            Return only valid JSON matching this schema:
            {
              "invoiceNumber": "string",
              "supplierName": "string",
              "supplierTaxId": "string|null",
              "buyerName": "string|null",
              "invoiceDate": "yyyy-MM-dd|null",
              "dueDate": "yyyy-MM-dd|null",
              "currency": "ISO-4217 code",
              "amount": 123.45,
              "paymentReference": "string|null",
              "confidence": 0.0,
              "anomalies": ["string"]
            }
            Use null when a value is genuinely missing. Do not invent values.
            """;

    private static final String REVIEW_SYSTEM_PROMPT = """
            You are the independent four-eye reviewer for invoice payments.
            Review extracted invoice values against the invoice text.
            Approve only if amount, currency, supplier, invoice number, and due date are internally consistent.
            Return only valid JSON matching this schema:
            {
              "decision": "APPROVED|REJECTED|NEEDS_MANUAL_REVIEW",
              "confidence": 0.0,
              "failedChecks": ["string"],
              "rationale": "string"
            }
            Use NEEDS_MANUAL_REVIEW if evidence is incomplete or ambiguous.
            """;

    private final PromptExecutor promptExecutor;
    private final ObjectMapper objectMapper;
    private final JsonResponseParser parser;

    public InvoiceKoogAgents(PromptExecutor promptExecutor, ObjectMapper objectMapper) {
        this.promptExecutor = promptExecutor;
        this.objectMapper = objectMapper;
        this.parser = new JsonResponseParser(objectMapper);
    }

    public InvoiceExtraction extractInvoice(String clientId, String invoiceReference, String invoiceText) {
        String prompt = """
                Client id: %s
                Invoice reference: %s

                Invoice text:
                %s
                """.formatted(clientId, nullToEmpty(invoiceReference), truncate(invoiceText));
        return parser.parse(runAgent(EXTRACTION_SYSTEM_PROMPT, prompt), InvoiceExtraction.class);
    }

    public FourEyeReview reviewInvoice(InvoiceExtraction extraction, String invoiceText) {
        String prompt = """
                Extracted invoice JSON:
                %s

                Source invoice text:
                %s
                """.formatted(toJson(extraction), truncate(invoiceText));
        return parser.parse(runAgent(REVIEW_SYSTEM_PROMPT, prompt), FourEyeReview.class);
    }

    private String runAgent(String systemPrompt, String userPrompt) {
        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(promptExecutor)
                .llmModel(OpenAIModels.Chat.GPT5Nano)
                .systemPrompt(systemPrompt)
                .build();
        return agent.run(userPrompt);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize agent context", ex);
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        int limit = 45_000;
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
