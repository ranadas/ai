package com.agent.liquidalts.invoiceagent.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Structured output of the extraction agent. */
public record InvoiceData(
        String invoiceNumber,
        String vendorName,
        String vendorId,
        String poNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String currency,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        BigDecimal grossAmount,
        List<LineItem> lineItems,
        /** Agent's self-assessed extraction confidence in [0.0, 1.0]. */
        double confidence
) {
    public InvoiceData {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
    }
}
