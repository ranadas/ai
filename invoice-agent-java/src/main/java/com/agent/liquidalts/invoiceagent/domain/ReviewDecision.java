package com.agent.liquidalts.invoiceagent.domain;

import java.time.Instant;

public record ReviewDecision(String reviewer, Decision decision, String comment, Instant decidedAt) {

    public static ReviewDecision of(String reviewer, Decision decision, String comment) {
        return new ReviewDecision(reviewer, decision, comment, Instant.now());
    }
}
