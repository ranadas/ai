package com.agent.liquidalts.invoiceagent.store

import com.agent.liquidalts.invoiceagent.domain.InvoiceRecord
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistence boundary for the pipeline. The in-memory implementation
 * keeps the demo self-contained; swap for a Spring Data JPA repository
 * (Postgres + Flyway) without touching callers.
 */
interface InvoiceStore {
    fun save(record: InvoiceRecord): InvoiceRecord
    fun findById(id: UUID): InvoiceRecord?
    fun findByIdempotencyKey(key: String): InvoiceRecord?
    fun update(id: UUID, mutation: (InvoiceRecord) -> InvoiceRecord): InvoiceRecord
}

@Repository
class InMemoryInvoiceStore : InvoiceStore {

    private val byId = ConcurrentHashMap<UUID, InvoiceRecord>()
    private val byIdempotencyKey = ConcurrentHashMap<String, UUID>()

    override fun save(record: InvoiceRecord): InvoiceRecord {
        // putIfAbsent guarantees first-writer-wins for duplicate submissions
        val existingId = byIdempotencyKey.putIfAbsent(record.idempotencyKey, record.id)
        if (existingId != null) return byId.getValue(existingId)
        byId[record.id] = record
        return record
    }

    override fun findById(id: UUID): InvoiceRecord? = byId[id]

    override fun findByIdempotencyKey(key: String): InvoiceRecord? =
        byIdempotencyKey[key]?.let { byId[it] }

    override fun update(id: UUID, mutation: (InvoiceRecord) -> InvoiceRecord): InvoiceRecord =
        byId.compute(id) { _, current ->
            requireNotNull(current) { "Invoice $id not found" }
            mutation(current).copy(updatedAt = Instant.now())
        }!!
}
