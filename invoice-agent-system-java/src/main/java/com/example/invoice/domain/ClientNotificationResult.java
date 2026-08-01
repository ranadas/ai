package com.example.invoice.domain;

public record ClientNotificationResult(boolean sent, String outcome, String channel, String message) { }
