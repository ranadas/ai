package com.example.invoiceagent.api;

import java.util.UUID;

import com.example.invoiceagent.domain.PaymentDecision;
import com.example.invoiceagent.domain.ProcessStatus;

public record ProcessInvoiceResponse(
        UUID processId,
        ProcessStatus status,
        PaymentDecision paymentDecision,
        String message,
        InvoiceExtraction extraction,
        FourEyeReview review,
        GenevaUpdateResult geneva,
        NavControlPackResult navControlPack,
        ClientNotificationResult clientNotification
) {
}
