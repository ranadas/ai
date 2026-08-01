package com.agent.liquidalts.invoiceagent.rules;

import java.math.BigDecimal;

public record PurchaseOrder(String poNumber, String vendorId, BigDecimal amount, String currency, boolean open) {}
