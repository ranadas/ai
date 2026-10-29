package com.example.invoice.domain;

import java.util.List;
import java.util.Map;

public record ValidationResult(boolean valid, List<ValidationCheck> checks, Map<String, String> enrichedFields) {
    public ValidationResult {
        checks = checks == null ? List.of() : List.copyOf(checks);
        enrichedFields = enrichedFields == null ? Map.of() : Map.copyOf(enrichedFields);
    }
}
