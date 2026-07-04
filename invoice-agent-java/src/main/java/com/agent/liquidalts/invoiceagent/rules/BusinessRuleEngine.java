package com.agent.liquidalts.invoiceagent.rules;

import com.agent.liquidalts.invoiceagent.domain.Finding;
import com.agent.liquidalts.invoiceagent.domain.InvoiceData;
import com.agent.liquidalts.invoiceagent.domain.Severity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic business rules. Anything with legal/financial teeth stays
 * in code, not in a prompt — the agent orchestrates, the rules decide.
 */
@Component
public class BusinessRuleEngine {

    private static final double MIN_EXTRACTION_CONFIDENCE = 0.75;

    public List<Finding> evaluate(InvoiceData data, Vendor vendor, PurchaseOrder po) {
        List<Finding> findings = new ArrayList<>();
        checkArithmetic(data, findings);
        checkVendor(data, vendor, findings);
        checkPurchaseOrder(data, vendor, po, findings);
        checkConfidence(data, findings);
        return List.copyOf(findings);
    }

    private void checkArithmetic(InvoiceData data, List<Finding> findings) {
        if (data.netAmount().add(data.taxAmount()).compareTo(data.grossAmount()) != 0) {
            findings.add(new Finding(Severity.BLOCKER, "AMOUNT_RECONCILIATION",
                    "net (%s) + tax (%s) != gross (%s)"
                            .formatted(data.netAmount(), data.taxAmount(), data.grossAmount())));
        }
        if (data.grossAmount().compareTo(BigDecimal.ZERO) <= 0) {
            findings.add(new Finding(Severity.BLOCKER, "POSITIVE_AMOUNT", "Gross amount must be positive"));
        }
    }

    private void checkVendor(InvoiceData data, Vendor vendor, List<Finding> findings) {
        if (vendor == null) {
            findings.add(new Finding(Severity.BLOCKER, "VENDOR_UNKNOWN",
                    "Vendor '%s' not found in vendor master".formatted(data.vendorName())));
        } else if (!vendor.active()) {
            findings.add(new Finding(Severity.BLOCKER, "VENDOR_INACTIVE",
                    "Vendor %s is inactive".formatted(vendor.vendorId())));
        }
    }

    private void checkPurchaseOrder(InvoiceData data, Vendor vendor, PurchaseOrder po, List<Finding> findings) {
        if (data.poNumber() == null) {
            findings.add(new Finding(Severity.WARNING, "NO_PO_REFERENCE",
                    "Invoice carries no PO reference — manual coding required"));
            return;
        }
        if (po == null) {
            findings.add(new Finding(Severity.BLOCKER, "PO_NOT_FOUND",
                    "PO %s not found".formatted(data.poNumber())));
            return;
        }
        if (!po.open()) {
            findings.add(new Finding(Severity.BLOCKER, "PO_CLOSED",
                    "PO %s is closed".formatted(po.poNumber())));
        }
        if (vendor != null && !po.vendorId().equals(vendor.vendorId())) {
            findings.add(new Finding(Severity.BLOCKER, "PO_VENDOR_MISMATCH",
                    "PO belongs to %s, invoice vendor is %s".formatted(po.vendorId(), vendor.vendorId())));
        }
        if (data.grossAmount().compareTo(po.amount()) > 0) {
            findings.add(new Finding(Severity.WARNING, "PO_AMOUNT_EXCEEDED",
                    "Invoice %s exceeds PO amount %s".formatted(data.grossAmount(), po.amount())));
        }
    }

    private void checkConfidence(InvoiceData data, List<Finding> findings) {
        if (data.confidence() < MIN_EXTRACTION_CONFIDENCE) {
            findings.add(new Finding(Severity.WARNING, "LOW_EXTRACTION_CONFIDENCE",
                    "Extraction confidence %s below %s".formatted(data.confidence(), MIN_EXTRACTION_CONFIDENCE)));
        }
    }
}
