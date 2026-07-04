package com.example.invoice.agent;

import com.example.invoice.domain.ExtractedInvoice;
import com.example.invoice.domain.InvoiceContext;
import com.example.invoice.domain.ValidationCheck;
import com.example.invoice.domain.ValidationResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class DataValidationAndEnrichmentAgent implements InvoiceAgent<ExtractedInvoice, ValidationResult> {
    @Override
    public String name() {
        return "2. Data Validation & Enrichment Agent";
    }

    @Override
    public ValidationResult run(ExtractedInvoice input, InvoiceContext ctx) {
        List<ValidationCheck> checks = List.of(
                new ValidationCheck("Invoice number present", hasText(input.invoiceNumber()), "Invoice number is mandatory"),
                new ValidationCheck("Vendor present", hasText(input.vendorName()) || hasText(input.vendorId()), "Vendor name or vendor id is mandatory"),
                new ValidationCheck("Amount positive", amount(input.grossAmount()).compareTo(BigDecimal.ZERO) > 0, "Gross amount must be positive"),
                new ValidationCheck("Currency present", hasText(input.currency()), "Currency is mandatory"),
                new ValidationCheck("Extraction confidence", input.confidence() >= 0.70, "Confidence should be >= 0.70 for straight-through processing")
        );
        boolean valid = checks.stream().allMatch(ValidationCheck::passed);
        ValidationResult result = new ValidationResult(valid, checks, Map.of(
                "vendorMasterStatus", hasText(input.vendorName()) || hasText(input.vendorId()) ? "MATCHED_OR_PENDING_MATCH" : "MISSING",
                "poGrContractStatus", "NOT_CONFIGURED_DEMO"
        ));
        long failed = checks.stream().filter(check -> !check.passed()).count();
        ctx.record(name(), "validate", "valid=" + result.valid() + "; failed=" + failed);
        return result;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
