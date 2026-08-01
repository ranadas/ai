package com.example.invoice

import com.example.invoice.domain.InvoiceProcessRequest
import com.example.invoice.domain.ProcessingStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = [
    "invoice.geneva.base-url=http://localhost:1",
    "invoice.ai.openai.api-key=",
    "spring.ai.openai.api-key="
])
class InvoiceProcessingOrchestratorTest @Autowired constructor(
    private val orchestrator: com.example.invoice.agent.InvoiceProcessingOrchestrator
) {
    @Test
    fun `processes invoice needing review when confidence is low`() {
        val response = orchestrator.process(InvoiceProcessRequest(
            manualText = "Invoice Number: INV-123\nVendor: Acme Fund Services\nCurrency: USD\nTotal: 1000.00"
        ))
        assertNotNull(response.invoiceId)
        assertEquals(ProcessingStatus.REVIEW_REQUIRED, response.status)
    }
}
