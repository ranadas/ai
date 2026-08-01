package com.example.invoice.agent;

import com.example.invoice.domain.ExtractedInvoice;
import com.example.invoice.domain.InvoiceContext;
import com.example.invoice.domain.InvoiceLineItem;
import com.example.invoice.domain.InvoiceProcessRequest;
import com.example.invoice.integrations.OpenAiHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class InvoiceIngestAndUnderstandAgent implements InvoiceAgent<InvoiceProcessRequest, ExtractedInvoice> {
    private final OpenAiHttpClient openAi;
    private final ObjectMapper mapper;

    public InvoiceIngestAndUnderstandAgent(OpenAiHttpClient openAi, ObjectMapper mapper) {
        this.openAi = openAi;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "1. Invoice Ingest & Understanding Agent";
    }

    @Override
    public ExtractedInvoice run(InvoiceProcessRequest input, InvoiceContext ctx) {
        ctx.record(name(), "extract", "Started invoice extraction");
        String text = input.manualText() != null
                ? input.manualText()
                : "PDF base64 size=" + (input.pdfBase64() == null ? 0 : input.pdfBase64().length()) + "; filename=" + input.fileName();

        String system = """
                You extract invoice data. Return strict JSON with keys: invoiceNumber, vendorName, vendorId,
                clientName, currency, grossAmount, netAmount, taxAmount, invoiceDate, dueDate,
                paymentReference, lineItems, confidence. Dates must be yyyy-MM-dd. Amounts numeric.
                """;

        String json;
        try {
            json = openAi.chat(system, text);
        } catch (Exception ex) {
            json = "{}";
        }

        ExtractedInvoice extracted = parseOrFallback(json, text);
        ctx.record(name(), "extract", "Extracted invoice=" + valueOr(extracted.invoiceNumber(), "UNKNOWN") + ", confidence=" + extracted.confidence());
        return extracted;
    }

    private ExtractedInvoice parseOrFallback(String json, String text) {
        try {
            JsonNode node = mapper.readTree(json);
            return new ExtractedInvoice(
                    textOrNull(node, "invoiceNumber"),
                    textOrNull(node, "vendorName"),
                    textOrNull(node, "vendorId"),
                    textOrNull(node, "clientName"),
                    textOrNull(node, "currency"),
                    decimalOrNull(node, "grossAmount"),
                    decimalOrNull(node, "netAmount"),
                    decimalOrNull(node, "taxAmount"),
                    dateOrNull(node, "invoiceDate"),
                    dateOrNull(node, "dueDate"),
                    textOrNull(node, "paymentReference"),
                    List.<InvoiceLineItem>of(),
                    node.path("confidence").asDouble(0.55),
                    json
            );
        } catch (Exception ignored) {
            return new ExtractedInvoice(
                    match(text, "(?i)invoice\\s*(no|number|#)?[:\\s-]+([A-Z0-9-]+)", 2),
                    trim(match(text, "(?i)vendor[:\\s-]+([^\\n]+)", 1)),
                    null,
                    trim(match(text, "(?i)client[:\\s-]+([^\\n]+)", 1)),
                    match(text, "\\b(USD|EUR|GBP|INR|CHF)\\b", 1),
                    parseDecimal(match(text, "(?i)(total|gross)[:\\s]+([0-9,.]+)", 2)),
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    0.35,
                    json
            );
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isNumber() || value.isTextual()) {
            return parseDecimal(value.asText());
        }
        return null;
    }

    private static LocalDate dateOrNull(JsonNode node, String field) {
        String value = textOrNull(node, field);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String match(String text, String regex, int group) {
        Matcher matcher = Pattern.compile(regex).matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group(group) : null;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
