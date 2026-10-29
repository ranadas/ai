package com.agent.liquidalts.invoiceagent.agent.tools;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;
import com.agent.liquidalts.invoiceagent.audit.AuditTrail;
import com.agent.liquidalts.invoiceagent.domain.Decision;
import com.agent.liquidalts.invoiceagent.domain.InvoiceData;
import com.agent.liquidalts.invoiceagent.domain.InvoiceRecord;
import com.agent.liquidalts.invoiceagent.domain.InvoiceStatus;
import com.agent.liquidalts.invoiceagent.integration.ClientNotification;
import com.agent.liquidalts.invoiceagent.integration.ClientNotifier;
import com.agent.liquidalts.invoiceagent.integration.GenevaClient;
import com.agent.liquidalts.invoiceagent.integration.GenevaInvoiceResponse;
import com.agent.liquidalts.invoiceagent.integration.Outcome;
import com.agent.liquidalts.invoiceagent.service.NavControlPackService;
import com.agent.liquidalts.invoiceagent.store.InvoiceStore;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * Tools for the Post & Integration and Control Pack & Notification agents
 * (agents 4 & 5 on the diagram).
 *
 * Every tool re-checks state before acting — defence in depth: even if the
 * LLM calls tools out of order, an unapproved invoice can never reach Geneva
 * and a payment that never happened can never be confirmed.
 */
@Component
public class PostingTools implements ToolSet {

    private final InvoiceStore store;
    private final GenevaClient geneva;
    private final NavControlPackService controlPacks;
    private final ClientNotifier notifier;
    private final AuditTrail audit;

    public PostingTools(InvoiceStore store,
                        GenevaClient geneva,
                        NavControlPackService controlPacks,
                        ClientNotifier notifier,
                        AuditTrail audit) {
        this.store = store;
        this.geneva = geneva;
        this.controlPacks = controlPacks;
        this.notifier = notifier;
        this.audit = audit;
    }

    @Tool
    @LLMDescription("Pushes the approved invoice and its validated values to Geneva via its REST API. "
            + "Idempotent: safe to retry. Returns the Geneva reference, or ERROR with a reason.")
    public String pushInvoiceToGeneva(
            @LLMDescription("The internal invoice id (UUID)") String invoiceId) {

        UUID id = UUID.fromString(invoiceId);
        InvoiceRecord record = store.findById(id).orElse(null);
        if (record == null) {
            return "ERROR: invoice " + invoiceId + " not found";
        }

        // Hard guard: only four-eye-approved invoices may be posted.
        boolean approved = record.reviewDecision() != null
                && record.reviewDecision().decision() == Decision.APPROVE;
        if (!approved || record.status() != InvoiceStatus.POSTING) {
            return "ERROR: invoice is not approved for posting (status=" + record.status() + ")";
        }
        // Idempotency: if a previous attempt already succeeded, return it.
        if (record.genevaReference() != null) {
            return "OK: already posted, genevaReference=" + record.genevaReference();
        }

        InvoiceData data = record.extractedData();
        if (data == null) {
            return "ERROR: no extracted data";
        }
        String vendorId = record.validationReport() != null
                ? record.validationReport().enrichedVendorId() : null;
        if (vendorId == null) {
            return "ERROR: vendor was never resolved against the vendor master";
        }

        try {
            GenevaInvoiceResponse response = geneva.upsertInvoice(
                    GenevaClient.toRequest(record.id().toString(), data, vendorId),
                    record.idempotencyKey());
            store.update(id, r -> r.toBuilder().genevaReference(response.genevaReference()).build());
            audit.record(id, "posting-agent", "GENEVA_PUSHED", "reference=" + response.genevaReference());
            return "OK: genevaReference=" + response.genevaReference();
        } catch (RuntimeException e) {
            audit.record(id, "posting-agent", "GENEVA_PUSH_FAILED", String.valueOf(e.getMessage()));
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool
    @LLMDescription("Creates the audit-ready NAV control pack for the invoice and stores it. "
            + "Call only after the Geneva push has succeeded.")
    public String createNavControlPack(
            @LLMDescription("The internal invoice id (UUID)") String invoiceId) {

        UUID id = UUID.fromString(invoiceId);
        InvoiceRecord record = store.findById(id).orElse(null);
        if (record == null) {
            return "ERROR: invoice " + invoiceId + " not found";
        }
        if (record.genevaReference() == null) {
            return "ERROR: cannot create control pack before a successful Geneva push";
        }
        String pack = controlPacks.build(record);
        store.update(id, r -> r.toBuilder().controlPack(pack).build());
        audit.record(id, "control-pack-agent", "CONTROL_PACK_CREATED", "size=" + pack.length() + " chars");
        return "OK: control pack created";
    }

    @Tool
    @LLMDescription("Sends the final response to the client: SUCCESS to confirm the payment, "
            + "or FAILURE with the reason and action required to reject it. Terminal step.")
    public String notifyClient(
            @LLMDescription("The internal invoice id (UUID)") String invoiceId,
            @LLMDescription("SUCCESS or FAILURE") String outcome,
            @LLMDescription("Human-readable confirmation, or reason and action required on failure") String message) {

        UUID id = UUID.fromString(invoiceId);
        InvoiceRecord record = store.findById(id).orElse(null);
        if (record == null) {
            return "ERROR: invoice " + invoiceId + " not found";
        }

        Outcome parsed;
        try {
            parsed = Outcome.valueOf(outcome.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return "ERROR: outcome must be SUCCESS or FAILURE";
        }

        // A SUCCESS notification without a Geneva reference would confirm a
        // payment that never happened — block it regardless of what the agent thinks.
        if (parsed == Outcome.SUCCESS && record.genevaReference() == null) {
            return "ERROR: cannot notify SUCCESS — invoice was not posted to Geneva";
        }

        String invoiceNumber = record.extractedData() != null
                ? record.extractedData().invoiceNumber() : null;
        notifier.notify(new ClientNotification(id, invoiceNumber, parsed, message));

        InvoiceStatus terminal = parsed == Outcome.SUCCESS
                ? InvoiceStatus.COMPLETED : InvoiceStatus.POSTING_FAILED;
        store.update(id, r -> r.toBuilder()
                .status(terminal)
                .failureReason(parsed == Outcome.FAILURE ? message : null)
                .build());
        audit.record(id, "notification-agent", "CLIENT_NOTIFIED", "outcome=" + parsed);
        return "OK: client notified (" + parsed + ")";
    }
}
