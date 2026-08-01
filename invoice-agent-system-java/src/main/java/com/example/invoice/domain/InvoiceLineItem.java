package com.example.invoice.domain;

import java.math.BigDecimal;

public record InvoiceLineItem(String description, BigDecimal amount, String glAccount, String fund) { }
