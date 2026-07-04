package com.example.invoice.store;

import com.example.invoice.domain.InvoiceProcessResponse;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InvoiceMemoryStore {
    private final ConcurrentMap<String, InvoiceProcessResponse> results = new ConcurrentHashMap<>();

    public InvoiceProcessResponse save(InvoiceProcessResponse response) {
        results.put(response.invoiceId(), response);
        return response;
    }

    public Optional<InvoiceProcessResponse> find(String invoiceId) {
        return Optional.ofNullable(results.get(invoiceId));
    }
}
