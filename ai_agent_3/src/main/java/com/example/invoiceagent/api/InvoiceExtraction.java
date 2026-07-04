package com.example.invoiceagent.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceExtraction(
        String invoiceNumber,
        String supplierName,
        String supplierTaxId,
        String buyerName,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String currency,
        BigDecimal amount,
        String paymentReference,
        double confidence,
        List<String> anomalies
) {
}
