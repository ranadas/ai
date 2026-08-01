package com.agent.liquidalts.invoiceagent.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
        UUID invoiceId,
        String actor,     // human user id or agent name
        String action,    // e.g. INVOICE_SUBMITTED, GENEVA_PUSHED
        String detail,
        Instant at
) {
    public static AuditEvent of(UUID invoiceId, String actor, String action, String detail) {
        return new AuditEvent(invoiceId, actor, action, detail, Instant.now());
    }
}
