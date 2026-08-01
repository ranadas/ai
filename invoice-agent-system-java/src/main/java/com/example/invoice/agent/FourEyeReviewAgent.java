package com.example.invoice.agent;

import com.example.invoice.domain.ExtractedInvoice;
import com.example.invoice.domain.InvoiceContext;
import com.example.invoice.domain.ReviewDecision;
import com.example.invoice.domain.ReviewResult;
import com.example.invoice.domain.ValidationResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class FourEyeReviewAgent implements InvoiceAgent<FourEyeReviewAgent.ReviewInput, ReviewResult> {
    public record ReviewInput(ExtractedInvoice invoice, ValidationResult validation) { }

    @Override
    public String name() {
        return "3. Four Eye Review Agent";
    }

    @Override
    public ReviewResult run(ReviewInput input, InvoiceContext ctx) {
        ExtractedInvoice invoice = input.invoice();
        ValidationResult validation = input.validation();
        BigDecimal grossAmount = invoice.grossAmount() == null ? BigDecimal.ZERO : invoice.grossAmount();
        boolean requiresManual = !validation.valid()
                || invoice.confidence() < 0.85
                || grossAmount.compareTo(new BigDecimal("100000")) > 0;

        ReviewResult result = requiresManual
                ? new ReviewResult(ReviewDecision.NEEDS_MANUAL_REVIEW, 2, 0, List.of("Four-eye approval required before Geneva posting."))
                : new ReviewResult(ReviewDecision.APPROVED, 2, 2, List.of("Auto-approved by policy for high confidence, low-risk invoice."));
        ctx.record(name(), "review", "decision=" + result.decision());
        return result;
    }
}
