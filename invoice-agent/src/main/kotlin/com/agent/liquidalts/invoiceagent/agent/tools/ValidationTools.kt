package com.agent.liquidalts.invoiceagent.agent.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.agent.liquidalts.invoiceagent.audit.AuditTrail
import com.agent.liquidalts.invoiceagent.domain.Severity
import com.agent.liquidalts.invoiceagent.domain.ValidationReport
import com.agent.liquidalts.invoiceagent.rules.BusinessRuleEngine
import com.agent.liquidalts.invoiceagent.rules.PurchaseOrderRepository
import com.agent.liquidalts.invoiceagent.rules.VendorMasterRepository
import com.agent.liquidalts.invoiceagent.store.InvoiceStore
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Tools for the Data Validation & Enrichment agent (agent 2 on the diagram).
 *
 * Design principle: the LLM passes *references* (invoice id, vendor id),
 * never re-types financial figures. All figures flow tool-to-tool through
 * the store, so a hallucinated amount can never reach the rule engine.
 */
@Component
class ValidationTools(
    private val store: InvoiceStore,
    private val vendorMaster: VendorMasterRepository,
    private val purchaseOrders: PurchaseOrderRepository,
    private val ruleEngine: BusinessRuleEngine,
    private val audit: AuditTrail,
    private val objectMapper: ObjectMapper,
) : ToolSet {

    @Tool
    @LLMDescription("Returns the extracted invoice data (JSON) for the given invoice id.")
    fun getExtractedInvoice(
        @LLMDescription("The internal invoice id (UUID)") invoiceId: String,
    ): String {
        val record = store.findById(UUID.fromString(invoiceId))
            ?: return "ERROR: invoice $invoiceId not found"
        return record.extractedData
            ?.let { objectMapper.writeValueAsString(it) }
            ?: "ERROR: no extracted data on invoice $invoiceId"
    }

    @Tool
    @LLMDescription(
        "Looks up a vendor in the vendor master by exact vendor id (e.g. V-1001) or a " +
            "fragment of the vendor name. Returns the canonical vendor record or NOT_FOUND."
    )
    fun vendorMasterLookup(
        @LLMDescription("Vendor id or name fragment taken from the invoice") query: String,
    ): String {
        val vendor = vendorMaster.findByNameOrId(query) ?: return "NOT_FOUND"
        return objectMapper.writeValueAsString(vendor)
    }

    @Tool
    @LLMDescription("Looks up a purchase order by PO number. Returns the PO record or NOT_FOUND.")
    fun purchaseOrderLookup(
        @LLMDescription("PO number, e.g. PO-88801") poNumber: String,
    ): String {
        val po = purchaseOrders.findByNumber(poNumber) ?: return "NOT_FOUND"
        return objectMapper.writeValueAsString(po)
    }

    @Tool
    @LLMDescription(
        "Runs the deterministic business rule engine against the stored invoice data using the " +
            "resolved vendor id and PO number, persists the validation report, and returns the findings. " +
            "Call this exactly once, after resolving the vendor and PO."
    )
    fun runBusinessRules(
        @LLMDescription("The internal invoice id (UUID)") invoiceId: String,
        @LLMDescription("Resolved vendor id from vendorMasterLookup, or empty if unresolved") vendorId: String,
        @LLMDescription("Resolved PO number from purchaseOrderLookup, or empty if none") poNumber: String,
    ): String {
        val id = UUID.fromString(invoiceId)
        val record = store.findById(id) ?: return "ERROR: invoice $invoiceId not found"
        val data = record.extractedData ?: return "ERROR: no extracted data on invoice $invoiceId"

        val vendor = vendorId.takeIf { it.isNotBlank() }?.let { vendorMaster.findByNameOrId(it) }
        val po = poNumber.takeIf { it.isNotBlank() }?.let { purchaseOrders.findByNumber(it) }

        val findings = ruleEngine.evaluate(data, vendor, po)
        val report = ValidationReport(
            passed = findings.none { it.severity == Severity.BLOCKER },
            findings = findings,
            enrichedVendorId = vendor?.vendorId,
            matchedPoNumber = po?.poNumber,
        )
        store.update(id) { it.copy(validationReport = report) }
        audit.record(id, "validation-agent", "RULES_EVALUATED",
            "passed=${report.passed}, findings=${findings.size}")
        return objectMapper.writeValueAsString(report)
    }
}
