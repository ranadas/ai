package com.agent.liquidalts.invoiceagent.integration;

import java.math.BigDecimal;

public record GenevaInvoiceRequest(
        String externalReference,
        String vendorId,
        String invoiceNumber,
        String currency,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        BigDecimal grossAmount,
        String poNumber
) {}
