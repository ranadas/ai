package com.agent.liquidalts.invoiceagent.store;

import com.agent.liquidalts.invoiceagent.domain.InvoiceRecord;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

@Repository
public class InMemoryInvoiceStore implements InvoiceStore {

    private final Map<UUID, InvoiceRecord> byId = new ConcurrentHashMap<>();
    private final Map<String, UUID> byIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public InvoiceRecord save(InvoiceRecord record) {
        // putIfAbsent guarantees first-writer-wins for duplicate submissions
        UUID existingId = byIdempotencyKey.putIfAbsent(record.idempotencyKey(), record.id());
        if (existingId != null) {
            return byId.get(existingId);
        }
        byId.put(record.id(), record);
        return record;
    }

    @Override
    public Optional<InvoiceRecord> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<InvoiceRecord> findByIdempotencyKey(String key) {
        return Optional.ofNullable(byIdempotencyKey.get(key)).map(byId::get);
    }

    @Override
    public InvoiceRecord update(UUID id, UnaryOperator<InvoiceRecord> mutation) {
        return byId.compute(id, (key, current) ->
                mutation.apply(Objects.requireNonNull(current, () -> "Invoice " + id + " not found")));
    }
}
