package com.example.invoice.domain;

import java.time.Instant;

public record AuditEvent(Instant timestamp, String agent, String action, String outcome) { }
