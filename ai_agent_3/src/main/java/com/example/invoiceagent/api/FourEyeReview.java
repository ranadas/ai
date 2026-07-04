package com.example.invoiceagent.api;

import java.util.List;

import com.example.invoiceagent.domain.ReviewDecision;

public record FourEyeReview(
        ReviewDecision decision,
        double confidence,
        List<String> failedChecks,
        String rationale
) {
}
