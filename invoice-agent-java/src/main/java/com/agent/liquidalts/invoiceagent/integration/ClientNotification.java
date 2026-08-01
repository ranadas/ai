package com.agent.liquidalts.invoiceagent.integration;

import java.util.UUID;

public record ClientNotification(UUID invoiceId, String invoiceNumber, Outcome outcome, String message) {}
