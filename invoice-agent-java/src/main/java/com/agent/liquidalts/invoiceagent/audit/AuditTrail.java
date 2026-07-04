package com.agent.liquidalts.invoiceagent.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * End-to-end audit trail: who/what did what, when — including every agent
 * tool invocation. In production this appends to an immutable store and
 * streams each event to a Kafka {@code invoice.audit} topic; this method
 * is the single seam to extend.
 */
@Component
public class AuditTrail {

    private static final Logger log = LoggerFactory.getLogger(AuditTrail.class);

    private final Map<UUID, CopyOnWriteArrayList<AuditEvent>> events = new ConcurrentHashMap<>();

    public void record(UUID invoiceId, String actor, String action, String detail) {
        AuditEvent event = AuditEvent.of(invoiceId, actor, action, detail);
        events.computeIfAbsent(invoiceId, k -> new CopyOnWriteArrayList<>()).add(event);
        log.info("AUDIT invoice={} actor={} action={} detail={}", invoiceId, actor, action, detail);
    }

    public List<AuditEvent> forInvoice(UUID invoiceId) {
        var list = events.get(invoiceId);
        return list == null ? List.of() : List.copyOf(list);
    }
}
