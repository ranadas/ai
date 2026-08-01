package com.agent.liquidalts.invoiceagent.service;

import java.util.UUID;

public class InvoiceNotFoundException extends RuntimeException {
    public InvoiceNotFoundException(UUID invoiceId) {
        super("Invoice " + invoiceId + " not found");
    }
}
