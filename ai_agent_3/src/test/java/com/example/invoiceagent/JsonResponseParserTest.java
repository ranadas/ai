package com.example.invoiceagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.invoiceagent.agent.JsonResponseParser;
import com.example.invoiceagent.api.InvoiceExtraction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class JsonResponseParserTest {

    private final JsonResponseParser parser = new JsonResponseParser(new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void parsesJsonWrappedInMarkdownFence() {
        String response = """
                ```json
                {
                  "invoiceNumber": "INV-100",
                  "supplierName": "Acme Ltd",
                  "supplierTaxId": "GB123",
                  "buyerName": "Client Fund",
                  "invoiceDate": "2026-07-01",
                  "dueDate": "2026-07-31",
                  "currency": "USD",
                  "amount": 1250.25,
                  "paymentReference": "PAY-INV-100",
                  "confidence": 0.92,
                  "anomalies": []
                }
                ```
                """;

        InvoiceExtraction extraction = parser.parse(response, InvoiceExtraction.class);

        assertThat(extraction.invoiceNumber()).isEqualTo("INV-100");
        assertThat(extraction.amount()).isEqualByComparingTo(new BigDecimal("1250.25"));
        assertThat(extraction.dueDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(extraction.anomalies()).isEqualTo(List.of());
    }
}
