package com.agent.liquidalts.invoiceagent.domain;

import java.math.BigDecimal;

public record LineItem(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {}
