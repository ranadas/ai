package com.agent.liquidalts.invoiceagent.service;

import com.agent.liquidalts.invoiceagent.domain.InvoiceData;
import com.agent.liquidalts.invoiceagent.domain.InvoiceRecord;
import com.agent.liquidalts.invoiceagent.domain.ReviewDecision;
import com.agent.liquidalts.invoiceagent.domain.ValidationReport;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Builds the audit-ready NAV control pack: a self-contained record of what
 * was extracted, what the rules said, who approved it, and what landed in
 * Geneva. Rendered as Markdown here; the same model can feed a PDF renderer
 * or SharePoint/ECM upload.
 */
@Service
public class NavControlPackService {

    public String build(InvoiceRecord record) {
        InvoiceData data = record.extractedData();
        ValidationReport report = record.validationReport();
        ReviewDecision review = record.reviewDecision();

        StringBuilder pack = new StringBuilder()
                .append("# NAV Control Pack\n\n")
                .append("| Field | Value |\n|---|---|\n")
                .append(row("Control pack generated", Instant.now()))
                .append(row("Internal reference", record.id()))
                .append(row("Invoice number", data != null ? data.invoiceNumber() : "n/a"))
                .append(row("Vendor", vendorLine(data, report)))
                .append(row("PO reference", poLine(data, report)))
                .append(row("Gross amount", data != null ? data.currency() + " " + data.grossAmount() : "n/a"))
                .append(row("Extraction confidence", data != null ? data.confidence() : "n/a"))
                .append(row("Geneva reference", nvl(record.genevaReference())))
                .append("\n## Four-eye control\n")
                .append("- Submitted by: `").append(record.submittedBy())
                .append("` at ").append(record.createdAt()).append('\n');

        if (review != null) {
            pack.append("- Reviewed by: `").append(review.reviewer())
                    .append("` — **").append(review.decision())
                    .append("** at ").append(review.decidedAt()).append('\n');
            if (review.comment() != null) {
                pack.append("- Reviewer comment: ").append(review.comment()).append('\n');
            }
        }

        pack.append("\n## Validation findings\n");
        if (report == null || report.findings().isEmpty()) {
            pack.append("- No findings: all business rules passed.\n");
        } else {
            report.findings().forEach(f -> pack
                    .append("- [").append(f.severity()).append("] `")
                    .append(f.rule()).append("` — ").append(f.message()).append('\n'));
        }

        pack.append("\n## Agent review summary\n")
                .append(record.reviewSummary() != null ? record.reviewSummary() : "_none_")
                .append('\n');

        return pack.toString();
    }

    private static String vendorLine(InvoiceData data, ValidationReport report) {
        if (data == null) return "n/a";
        String resolvedId = report != null && report.enrichedVendorId() != null
                ? report.enrichedVendorId()
                : data.vendorId() != null ? data.vendorId() : "unmatched";
        return data.vendorName() + " (" + resolvedId + ")";
    }

    private static String poLine(InvoiceData data, ValidationReport report) {
        if (report != null && report.matchedPoNumber() != null) return report.matchedPoNumber();
        if (data != null && data.poNumber() != null) return data.poNumber();
        return "none";
    }

    private static String row(String field, Object value) {
        return "| " + field + " | " + value + " |\n";
    }

    private static String nvl(Object value) {
        return value != null ? value.toString() : "n/a";
    }
}
