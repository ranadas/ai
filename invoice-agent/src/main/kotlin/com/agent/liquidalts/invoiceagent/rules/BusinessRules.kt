package com.agent.liquidalts.invoiceagent.rules

import com.agent.liquidalts.invoiceagent.domain.Finding
import com.agent.liquidalts.invoiceagent.domain.InvoiceData
import com.agent.liquidalts.invoiceagent.domain.Severity
import org.springframework.stereotype.Component
import java.math.BigDecimal

data class Vendor(val vendorId: String, val name: String, val iban: String, val active: Boolean)
data class PurchaseOrder(val poNumber: String, val vendorId: String, val amount: BigDecimal, val currency: String, val open: Boolean)

/** Vendor master lookup — in production this fronts the vendor master service. */
@Component
class VendorMasterRepository {
    private val vendors = listOf(
        Vendor("V-1001", "Acme Fund Services Ltd", "IE29AIBK93115212345678", active = true),
        Vendor("V-1002", "Dublin Data Systems", "IE64BOFI90583812345678", active = true),
        Vendor("V-1003", "Meridian Consulting GmbH", "DE89370400440532013000", active = false),
    )

    fun findByNameOrId(query: String): Vendor? {
        val q = query.trim().lowercase()
        return vendors.firstOrNull { it.vendorId.lowercase() == q || it.name.lowercase().contains(q) }
    }
}

/** PO / GR lookup — in production this fronts the procurement service. */
@Component
class PurchaseOrderRepository {
    private val orders = listOf(
        PurchaseOrder("PO-88801", "V-1001", BigDecimal("12500.00"), "EUR", open = true),
        PurchaseOrder("PO-88802", "V-1002", BigDecimal("4300.00"), "EUR", open = true),
    )

    fun findByNumber(poNumber: String): PurchaseOrder? =
        orders.firstOrNull { it.poNumber.equals(poNumber.trim(), ignoreCase = true) }
}

/**
 * Deterministic business rules. Anything with legal/financial teeth stays
 * in code, not in a prompt — the agent orchestrates, the rules decide.
 */
@Component
class BusinessRuleEngine {

    fun evaluate(data: InvoiceData, vendor: Vendor?, po: PurchaseOrder?): List<Finding> = buildList {
        // Arithmetic integrity
        if (data.netAmount + data.taxAmount != data.grossAmount) {
            add(Finding(Severity.BLOCKER, "AMOUNT_RECONCILIATION",
                "net (${data.netAmount}) + tax (${data.taxAmount}) != gross (${data.grossAmount})"))
        }
        if (data.grossAmount <= BigDecimal.ZERO) {
            add(Finding(Severity.BLOCKER, "POSITIVE_AMOUNT", "Gross amount must be positive"))
        }

        // Vendor master
        when {
            vendor == null ->
                add(Finding(Severity.BLOCKER, "VENDOR_UNKNOWN", "Vendor '${data.vendorName}' not found in vendor master"))
            !vendor.active ->
                add(Finding(Severity.BLOCKER, "VENDOR_INACTIVE", "Vendor ${vendor.vendorId} is inactive"))
        }

        // PO matching (three-way-match lite)
        when {
            data.poNumber == null ->
                add(Finding(Severity.WARNING, "NO_PO_REFERENCE", "Invoice carries no PO reference — manual coding required"))
            po == null ->
                add(Finding(Severity.BLOCKER, "PO_NOT_FOUND", "PO ${data.poNumber} not found"))
            !po.open ->
                add(Finding(Severity.BLOCKER, "PO_CLOSED", "PO ${po.poNumber} is closed"))
            vendor != null && po.vendorId != vendor.vendorId ->
                add(Finding(Severity.BLOCKER, "PO_VENDOR_MISMATCH", "PO belongs to ${po.vendorId}, invoice vendor is ${vendor.vendorId}"))
            data.grossAmount > po.amount ->
                add(Finding(Severity.WARNING, "PO_AMOUNT_EXCEEDED", "Invoice ${data.grossAmount} exceeds PO amount ${po.amount}"))
        }

        // Extraction confidence gate
        if (data.confidence < 0.75) {
            add(Finding(Severity.WARNING, "LOW_EXTRACTION_CONFIDENCE", "Extraction confidence ${data.confidence} below 0.75"))
        }
    }
}
