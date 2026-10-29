package com.agent.liquidalts.invoiceagent.domain;

import java.util.List;

/** Output of the validation & enrichment agent. */
public record ValidationReport(
        boolean passed,
        List<Finding> findings,
        String enrichedVendorId,
        String matchedPoNumber
) {
    public ValidationReport {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
