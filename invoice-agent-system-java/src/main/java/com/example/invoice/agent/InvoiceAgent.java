package com.example.invoice.agent;

import com.example.invoice.domain.InvoiceContext;

public interface InvoiceAgent<I, O> {
    String name();
    O run(I input, InvoiceContext ctx);
}
