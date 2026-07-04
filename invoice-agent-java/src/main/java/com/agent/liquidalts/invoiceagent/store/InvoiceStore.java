package com.agent.liquidalts.invoiceagent.store;

import com.agent.liquidalts.invoiceagent.domain.InvoiceRecord;

import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Persistence boundary for the pipeline. The in-memory implementation keeps
 * the demo self-contained; swap for a Spring Data JPA repository
 * (Postgres + Flyway) without touching callers.
 */
public interface InvoiceStore {

    InvoiceRecord save(InvoiceRecord record);

    Optional<InvoiceRecord> findById(UUID id);

    Optional<InvoiceRecord> findByIdempotencyKey(String key);

    /** Atomic read-modify-write; the mutation runs under the map's lock. */
    InvoiceRecord update(UUID id, UnaryOperator<InvoiceRecord> mutation);
}
