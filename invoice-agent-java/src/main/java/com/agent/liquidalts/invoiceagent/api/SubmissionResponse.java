package com.agent.liquidalts.invoiceagent.api;

import com.agent.liquidalts.invoiceagent.domain.InvoiceStatus;

import java.util.UUID;

public record SubmissionResponse(UUID invoiceId, InvoiceStatus status, boolean duplicate) {}
