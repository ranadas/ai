package com.agent.liquidalts.invoiceagent.rules

import com.agent.liquidalts.invoiceagent.domain.InvoiceData
import com.agent.liquidalts.invoiceagent.domain.Severity
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertTrue

class BusinessRuleEngineTest {

    private val engine = BusinessRuleEngine()

    private fun invoice(
        net: String = "100.00",
        tax: String = "23.00",
        gross: String = "123.00",
        po: String? = "PO-88801",
        confidence: Double = 0.95,
    ) = InvoiceData(
        invoiceNumber = "INV-001",
        vendorName = "Acme Fund Services Ltd",
        poNumber = po,
        currency = "EUR",
        netAmount = BigDecimal(net),
        taxAmount = BigDecimal(tax),
        grossAmount = BigDecimal(gross),
        confidence = confidence,
    )

    private val activeVendor = Vendor("V-1001", "Acme Fund Services Ltd", "IE29AIBK93115212345678", active = true)
    private val openPo = PurchaseOrder("PO-88801", "V-1001", BigDecimal("12500.00"), "EUR", open = true)

    @Test
    fun `clean invoice produces no blockers`() {
        val findings = engine.evaluate(invoice(), activeVendor, openPo)
        assertTrue(findings.none { it.severity == Severity.BLOCKER })
    }

    @Test
    fun `amount mismatch is a blocker`() {
        val findings = engine.evaluate(invoice(gross = "999.99"), activeVendor, openPo)
        assertTrue(findings.any { it.rule == "AMOUNT_RECONCILIATION" && it.severity == Severity.BLOCKER })
    }

    @Test
    fun `unknown vendor is a blocker`() {
        val findings = engine.evaluate(invoice(), vendor = null, po = openPo)
        assertTrue(findings.any { it.rule == "VENDOR_UNKNOWN" })
    }

    @Test
    fun `vendor mismatch on PO is a blocker`() {
        val otherVendor = activeVendor.copy(vendorId = "V-9999")
        val findings = engine.evaluate(invoice(), otherVendor, openPo)
        assertTrue(findings.any { it.rule == "PO_VENDOR_MISMATCH" })
    }

    @Test
    fun `low extraction confidence is surfaced as a warning`() {
        val findings = engine.evaluate(invoice(confidence = 0.5), activeVendor, openPo)
        assertTrue(findings.any { it.rule == "LOW_EXTRACTION_CONFIDENCE" && it.severity == Severity.WARNING })
    }
}
