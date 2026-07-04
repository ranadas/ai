package com.agent.liquidalts.invoiceagent.domain;

/**
 * Lifecycle of an invoice through the agent pipeline. Mirrors the five
 * agents on the architecture diagram, with an explicit human-in-the-loop
 * gate at PENDING_REVIEW.
 */
public enum InvoiceStatus {
    RECEIVED,        // 1. ingested, PDF stored
    EXTRACTING,      // 1. extraction agent running
    VALIDATING,      // 2. validation & enrichment agent running
    PENDING_REVIEW,  // 3. waiting for four-eye human approval
    REJECTED,        // 3. reviewer rejected — terminal
    POSTING,         // 4. posting agent pushing to Geneva
    POSTING_FAILED,  // 4. Geneva push failed — terminal (client notified)
    COMPLETED,       // 5. posted, control pack created, client notified
    FAILED           // pipeline error before review — terminal
}
