package com.agent.liquidalts.invoiceagent.extraction

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Component

/**
 * Deterministic text extraction — the LLM never sees raw bytes, only text.
 * For scanned invoices, plug an OCR engine (e.g. Tesseract/Textract) in
 * behind this same interface.
 */
@Component
class PdfTextExtractor {

    fun extractText(pdfBytes: ByteArray): String =
        Loader.loadPDF(pdfBytes).use { document ->
            PDFTextStripper().apply { sortByPosition = true }.getText(document)
        }
}
