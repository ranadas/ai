package com.example.invoice.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExtractedInvoice(
        String invoiceNumber,
        String vendorName,
        String vendorId,
        String clientName,
        String currency,
        BigDecimal grossAmount,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String paymentReference,
        List<InvoiceLineItem> lineItems,
        double confidence,
        String rawModelJson
) {
    public ExtractedInvoice {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
    }
}
