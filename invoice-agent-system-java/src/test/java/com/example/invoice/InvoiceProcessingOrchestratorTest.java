package com.example.invoice;

import com.example.invoice.agent.InvoiceProcessingOrchestrator;
import com.example.invoice.domain.InvoiceProcessRequest;
import com.example.invoice.domain.InvoiceProcessResponse;
import com.example.invoice.domain.ProcessingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "invoice.geneva.base-url=http://localhost:1",
        "invoice.ai.openai.api-key=",
        "spring.ai.openai.api-key="
})
class InvoiceProcessingOrchestratorTest {
    @Autowired
    private InvoiceProcessingOrchestrator orchestrator;

    @Test
    void processesInvoiceNeedingReviewWhenConfidenceIsLow() {
        InvoiceProcessRequest request = new InvoiceProcessRequest(
                "REST_JSON",
                null,
                null,
                null,
                "Invoice Number: INV-123\nVendor: Acme Fund Services\nCurrency: USD\nTotal: 1000.00",
                null,
                Map.of()
        );

        InvoiceProcessResponse response = orchestrator.process(request);
        assertNotNull(response.invoiceId());
        assertEquals(ProcessingStatus.REVIEW_REQUIRED, response.status());
    }
}
