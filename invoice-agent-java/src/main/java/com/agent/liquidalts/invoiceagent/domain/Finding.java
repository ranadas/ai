package com.agent.liquidalts.invoiceagent.domain;

public record Finding(Severity severity, String rule, String message) {}
