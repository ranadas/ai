package com.agent.liquidalts.invoiceagent.rules;

import com.agent.liquidalts.invoiceagent.domain.Finding;
import com.agent.liquidalts.invoiceagent.domain.InvoiceData;
import com.agent.liquidalts.invoiceagent.domain.Severity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessRuleEngineTest {

    private final BusinessRuleEngine engine = new BusinessRuleEngine();

    private final Vendor activeVendor =
            new Vendor("V-1001", "Acme Fund Services Ltd", "IE29AIBK93115212345678", true);
    private final PurchaseOrder openPo =
            new PurchaseOrder("PO-88801", "V-1001", new BigDecimal("12500.00"), "EUR", true);

    private static InvoiceData invoice(String net, String tax, String gross, String po, double confidence) {
        return new InvoiceData("INV-001", "Acme Fund Services Ltd", null, po,
                null, null, "EUR",
                new BigDecimal(net), new BigDecimal(tax), new BigDecimal(gross),
                List.of(), confidence);
    }

    private static InvoiceData cleanInvoice() {
        return invoice("100.00", "23.00", "123.00", "PO-88801", 0.95);
    }

    @Test
    void cleanInvoiceProducesNoBlockers() {
        List<Finding> findings = engine.evaluate(cleanInvoice(), activeVendor, openPo);
        assertThat(findings).noneMatch(f -> f.severity() == Severity.BLOCKER);
    }

    @Test
    void amountMismatchIsABlocker() {
        List<Finding> findings = engine.evaluate(
                invoice("100.00", "23.00", "999.99", "PO-88801", 0.95), activeVendor, openPo);
        assertThat(findings)
                .anyMatch(f -> f.rule().equals("AMOUNT_RECONCILIATION") && f.severity() == Severity.BLOCKER);
    }

    @Test
    void unknownVendorIsABlocker() {
        List<Finding> findings = engine.evaluate(cleanInvoice(), null, openPo);
        assertThat(findings).anyMatch(f -> f.rule().equals("VENDOR_UNKNOWN"));
    }

    @Test
    void poVendorMismatchIsABlocker() {
        Vendor otherVendor = new Vendor("V-9999", "Someone Else Ltd", "IE00XXXX", true);
        List<Finding> findings = engine.evaluate(cleanInvoice(), otherVendor, openPo);
        assertThat(findings).anyMatch(f -> f.rule().equals("PO_VENDOR_MISMATCH"));
    }

    @Test
    void lowExtractionConfidenceIsSurfacedAsWarning() {
        List<Finding> findings = engine.evaluate(
                invoice("100.00", "23.00", "123.00", "PO-88801", 0.5), activeVendor, openPo);
        assertThat(findings)
                .anyMatch(f -> f.rule().equals("LOW_EXTRACTION_CONFIDENCE") && f.severity() == Severity.WARNING);
    }
}
