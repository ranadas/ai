package com.example.invoiceagent.pdf;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfTextExtractor {

    public String extract(byte[] pdfBytes) {
        try (var document = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not extract text from PDF", ex);
        }
    }
}
