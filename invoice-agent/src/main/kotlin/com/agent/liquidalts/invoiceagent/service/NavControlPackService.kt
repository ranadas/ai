package com.agent.liquidalts.invoiceagent.service

import com.agent.liquidalts.invoiceagent.domain.InvoiceRecord
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Builds the audit-ready NAV control pack: a self-contained record of
 * what was extracted, what the rules said, who approved it, and what
 * landed in Geneva. Rendered as Markdown here; the same model can feed
 * a PDF renderer or SharePoint/ECM upload.
 */
@Service
class NavControlPackService {

    fun build(record: InvoiceRecord): String {
        val data = record.extractedData
        val report = record.validationReport
        val review = record.reviewDecision

        return buildString {
            appendLine("# NAV Control Pack")
            appendLine()
            appendLine("| Field | Value |")
            appendLine("|---|---|")
            appendLine("| Control pack generated | ${Instant.now()} |")
            appendLine("| Internal reference | ${record.id} |")
            appendLine("| Invoice number | ${data?.invoiceNumber ?: "n/a"} |")
            appendLine("| Vendor | ${data?.vendorName ?: "n/a"} (${report?.enrichedVendorId ?: data?.vendorId ?: "unmatched"}) |")
            appendLine("| PO reference | ${report?.matchedPoNumber ?: data?.poNumber ?: "none"} |")
            appendLine("| Gross amount | ${data?.currency ?: ""} ${data?.grossAmount ?: "n/a"} |")
            appendLine("| Extraction confidence | ${data?.confidence ?: "n/a"} |")
            appendLine("| Geneva reference | ${record.genevaReference ?: "n/a"} |")
            appendLine()
            appendLine("## Four-eye control")
            appendLine("- Submitted by: `${record.submittedBy}` at ${record.createdAt}")
            appendLine("- Reviewed by: `${review?.reviewer ?: "n/a"}` — **${review?.decision ?: "n/a"}** at ${review?.decidedAt ?: "n/a"}")
            review?.comment?.let { appendLine("- Reviewer comment: $it") }
            appendLine()
            appendLine("## Validation findings")
            if (report == null || report.findings.isEmpty()) {
                appendLine("- No findings: all business rules passed.")
            } else {
                report.findings.forEach { appendLine("- [${it.severity}] `${it.rule}` — ${it.message}") }
            }
            appendLine()
            appendLine("## Agent review summary")
            appendLine(record.reviewSummary ?: "_none_")
        }
    }
}
