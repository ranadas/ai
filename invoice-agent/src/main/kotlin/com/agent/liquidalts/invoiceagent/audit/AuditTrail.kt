package com.agent.liquidalts.invoiceagent.audit

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class AuditEvent(
    val invoiceId: UUID,
    val actor: String,          // human user id or agent name
    val action: String,         // e.g. INVOICE_SUBMITTED, GENEVA_PUSHED
    val detail: String,
    val at: Instant = Instant.now(),
)

/**
 * End-to-end audit trail: who/what did what, when — including every
 * agent tool invocation. In production this appends to an immutable
 * store and streams each event to a Kafka `invoice.audit` topic.
 */
@Component
class AuditTrail {

    private val log = LoggerFactory.getLogger(javaClass)
    private val events = ConcurrentHashMap<UUID, CopyOnWriteArrayList<AuditEvent>>()

    fun record(invoiceId: UUID, actor: String, action: String, detail: String) {
        val event = AuditEvent(invoiceId, actor, action, detail)
        events.computeIfAbsent(invoiceId) { CopyOnWriteArrayList() }.add(event)
        log.info("AUDIT invoice={} actor={} action={} detail={}", invoiceId, actor, action, detail)
    }

    fun forInvoice(invoiceId: UUID): List<AuditEvent> =
        events[invoiceId]?.toList() ?: emptyList()
}
