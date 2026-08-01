package com.agent.liquidalts.invoiceagent.service;

public class IllegalReviewStateException extends RuntimeException {
    public IllegalReviewStateException(String message) {
        super(message);
    }
}
