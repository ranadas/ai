package com.agent.liquidalts.invoiceagent.agent.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.agent.liquidalts.invoiceagent.audit.AuditTrail
import com.agent.liquidalts.invoiceagent.domain.Decision
import com.agent.liquidalts.invoiceagent.domain.InvoiceStatus
import com.agent.liquidalts.invoiceagent.integration.ClientNotification
import com.agent.liquidalts.invoiceagent.integration.ClientNotifier
import com.agent.liquidalts.invoiceagent.integration.GenevaClient
import com.agent.liquidalts.invoiceagent.integration.Outcome
import com.agent.liquidalts.invoiceagent.service.NavControlPackService
import com.agent.liquidalts.invoiceagent.store.InvoiceStore
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Tools for the Post & Integration and Control Pack & Notification agents
 * (agents 4 & 5 on the diagram).
 *
 * Every tool re-checks state before acting — defence in depth: even if the
 * LLM calls tools out of order, an unapproved invoice can never reach Geneva.
 */
@Component
class PostingTools(
    private val store: InvoiceStore,
    private val geneva: GenevaClient,
    private val controlPacks: NavControlPackService,
    private val notifier: ClientNotifier,
    private val audit: AuditTrail,
) : ToolSet {

    @Tool
    @LLMDescription(
        "Pushes the approved invoice and its validated values to Geneva via its REST API. " +
            "Idempotent: safe to retry. Returns the Geneva reference, or ERROR with a reason."
    )
    fun pushInvoiceToGeneva(
        @LLMDescription("The internal invoice id (UUID)") invoiceId: String,
    ): String {
        val id = UUID.fromString(invoiceId)
        val record = store.findById(id) ?: return "ERROR: invoice $invoiceId not found"

        // Hard guard: only four-eye-approved invoices may be posted.
        if (record.reviewDecision?.decision != Decision.APPROVE || record.status != InvoiceStatus.POSTING) {
            return "ERROR: invoice is not approved for posting (status=${record.status})"
        }
        // Idempotency: if a previous attempt already succeeded, return it.
        record.genevaReference?.let { return "OK: already posted, genevaReference=$it" }

        val data = record.extractedData ?: return "ERROR: no extracted data"
        val vendorId = record.validationReport?.enrichedVendorId
            ?: return "ERROR: vendor was never resolved against the vendor master"

        return try {
            val response = geneva.upsertInvoice(
                GenevaClient.toRequest(record.id.toString(), data, vendorId),
                idempotencyKey = record.idempotencyKey,
            )
            store.update(id) { it.copy(genevaReference = response.genevaReference) }
            audit.record(id, "posting-agent", "GENEVA_PUSHED", "reference=${response.genevaReference}")
            "OK: genevaReference=${response.genevaReference}"
        } catch (e: Exception) {
            audit.record(id, "posting-agent", "GENEVA_PUSH_FAILED", e.message ?: "unknown")
            "ERROR: ${e.message}"
        }
    }

    @Tool
    @LLMDescription(
        "Creates the audit-ready NAV control pack for the invoice and stores it. " +
            "Call only after the Geneva push has succeeded."
    )
    fun createNavControlPack(
        @LLMDescription("The internal invoice id (UUID)") invoiceId: String,
    ): String {
        val id = UUID.fromString(invoiceId)
        val record = store.findById(id) ?: return "ERROR: invoice $invoiceId not found"
        if (record.genevaReference == null) {
            return "ERROR: cannot create control pack before a successful Geneva push"
        }
        val pack = controlPacks.build(record)
        store.update(id) { it.copy(controlPack = pack) }
        audit.record(id, "control-pack-agent", "CONTROL_PACK_CREATED", "size=${pack.length} chars")
        return "OK: control pack created"
    }

    @Tool
    @LLMDescription(
        "Sends the final response to the client: SUCCESS to confirm the payment, " +
            "or FAILURE with the reason and action required to reject it. Terminal step."
    )
    fun notifyClient(
        @LLMDescription("The internal invoice id (UUID)") invoiceId: String,
        @LLMDescription("SUCCESS or FAILURE") outcome: String,
        @LLMDescription("Human-readable confirmation, or reason and action required on failure") message: String,
    ): String {
        val id = UUID.fromString(invoiceId)
        val record = store.findById(id) ?: return "ERROR: invoice $invoiceId not found"
        val parsed = runCatching { Outcome.valueOf(outcome.trim().uppercase()) }
            .getOrElse { return "ERROR: outcome must be SUCCESS or FAILURE" }

        // A SUCCESS notification without a Geneva reference would confirm a
        // payment that never happened — block it regardless of what the agent thinks.
        if (parsed == Outcome.SUCCESS && record.genevaReference == null) {
            return "ERROR: cannot notify SUCCESS — invoice was not posted to Geneva"
        }

        notifier.notify(
            ClientNotification(id, record.extractedData?.invoiceNumber, parsed, message)
        )
        val terminal = if (parsed == Outcome.SUCCESS) InvoiceStatus.COMPLETED else InvoiceStatus.POSTING_FAILED
        store.update(id) {
            it.copy(status = terminal, failureReason = message.takeIf { _ -> parsed == Outcome.FAILURE })
        }
        audit.record(id, "notification-agent", "CLIENT_NOTIFIED", "outcome=$parsed")
        return "OK: client notified ($parsed)"
    }
}
