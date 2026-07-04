package com.agent.liquidalts.invoiceagent.agent.tools;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;
import com.agent.liquidalts.invoiceagent.audit.AuditTrail;
import com.agent.liquidalts.invoiceagent.domain.InvoiceData;
import com.agent.liquidalts.invoiceagent.domain.InvoiceRecord;
import com.agent.liquidalts.invoiceagent.domain.Severity;
import com.agent.liquidalts.invoiceagent.domain.ValidationReport;
import com.agent.liquidalts.invoiceagent.rules.BusinessRuleEngine;
import com.agent.liquidalts.invoiceagent.rules.PurchaseOrder;
import com.agent.liquidalts.invoiceagent.rules.PurchaseOrderRepository;
import com.agent.liquidalts.invoiceagent.rules.Vendor;
import com.agent.liquidalts.invoiceagent.rules.VendorMasterRepository;
import com.agent.liquidalts.invoiceagent.store.InvoiceStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Tools for the Data Validation & Enrichment agent (agent 2 on the diagram).
 *
 * Design principle: the LLM passes <em>references</em> (invoice id, vendor id),
 * never re-types financial figures. All figures flow tool-to-tool through the
 * store, so a hallucinated amount can never reach the rule engine.
 */
@Component
public class ValidationTools implements ToolSet {

    private final InvoiceStore store;
    private final VendorMasterRepository vendorMaster;
    private final PurchaseOrderRepository purchaseOrders;
    private final BusinessRuleEngine ruleEngine;
    private final AuditTrail audit;
    private final ObjectMapper objectMapper;

    public ValidationTools(InvoiceStore store,
                           VendorMasterRepository vendorMaster,
                           PurchaseOrderRepository purchaseOrders,
                           BusinessRuleEngine ruleEngine,
                           AuditTrail audit,
                           ObjectMapper objectMapper) {
        this.store = store;
        this.vendorMaster = vendorMaster;
        this.purchaseOrders = purchaseOrders;
        this.ruleEngine = ruleEngine;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Tool
    @LLMDescription("Returns the extracted invoice data (JSON) for the given invoice id.")
    public String getExtractedInvoice(
            @LLMDescription("The internal invoice id (UUID)") String invoiceId) {
        return store.findById(UUID.fromString(invoiceId))
                .map(record -> record.extractedData() != null
                        ? toJson(record.extractedData())
                        : "ERROR: no extracted data on invoice " + invoiceId)
                .orElse("ERROR: invoice " + invoiceId + " not found");
    }

    @Tool
    @LLMDescription("Looks up a vendor in the vendor master by exact vendor id (e.g. V-1001) "
            + "or a fragment of the vendor name. Returns the canonical vendor record or NOT_FOUND.")
    public String vendorMasterLookup(
            @LLMDescription("Vendor id or name fragment taken from the invoice") String query) {
        return vendorMaster.findByNameOrId(query)
                .map(this::toJson)
                .orElse("NOT_FOUND");
    }

    @Tool
    @LLMDescription("Looks up a purchase order by PO number. Returns the PO record or NOT_FOUND.")
    public String purchaseOrderLookup(
            @LLMDescription("PO number, e.g. PO-88801") String poNumber) {
        return purchaseOrders.findByNumber(poNumber)
                .map(this::toJson)
                .orElse("NOT_FOUND");
    }

    @Tool
    @LLMDescription("Runs the deterministic business rule engine against the stored invoice data "
            + "using the resolved vendor id and PO number, persists the validation report, and "
            + "returns the findings. Call this exactly once, after resolving the vendor and PO.")
    public String runBusinessRules(
            @LLMDescription("The internal invoice id (UUID)") String invoiceId,
            @LLMDescription("Resolved vendor id from vendorMasterLookup, or empty if unresolved") String vendorId,
            @LLMDescription("Resolved PO number from purchaseOrderLookup, or empty if none") String poNumber) {

        UUID id = UUID.fromString(invoiceId);
        InvoiceRecord record = store.findById(id).orElse(null);
        if (record == null) {
            return "ERROR: invoice " + invoiceId + " not found";
        }
        InvoiceData data = record.extractedData();
        if (data == null) {
            return "ERROR: no extracted data on invoice " + invoiceId;
        }

        Vendor vendor = isBlank(vendorId) ? null
                : vendorMaster.findByNameOrId(vendorId).orElse(null);
        PurchaseOrder po = isBlank(poNumber) ? null
                : purchaseOrders.findByNumber(poNumber).orElse(null);

        var findings = ruleEngine.evaluate(data, vendor, po);
        var report = new ValidationReport(
                findings.stream().noneMatch(f -> f.severity() == Severity.BLOCKER),
                findings,
                vendor != null ? vendor.vendorId() : null,
                po != null ? po.poNumber() : null
        );
        store.update(id, r -> r.toBuilder().validationReport(report).build());
        audit.record(id, "validation-agent", "RULES_EVALUATED",
                "passed=%s, findings=%d".formatted(report.passed(), findings.size()));
        return toJson(report);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "ERROR: failed to serialise result — " + e.getOriginalMessage();
        }
    }
}
