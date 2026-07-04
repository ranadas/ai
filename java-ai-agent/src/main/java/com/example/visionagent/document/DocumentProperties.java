package com.example.visionagent.document;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for converting uploaded documents (PDFs) into images.
 */
@ConfigurationProperties(prefix = "vision-agent.document")
public record DocumentProperties(

        /** Rendering resolution for PDF pages. Higher = sharper but larger images. */
        int pdfDpi,

        /** Safety cap on how many PDF pages are rendered and sent to the model. */
        int maxPages
) {

    public DocumentProperties {
        if (pdfDpi <= 0) {
            pdfDpi = 150;
        }
        if (maxPages <= 0) {
            maxPages = 10;
        }
    }
}
