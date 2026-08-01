package com.example.visionagent.document;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts an uploaded file into one or more image {@link Media} items that a
 * vision LLM can consume.
 *
 * <ul>
 *   <li>An image upload is passed through as a single media item.</li>
 *   <li>A PDF upload is rendered page-by-page to PNG images (capped by
 *       {@link DocumentProperties#maxPages()}). Rendering pages works for both
 *       text and scanned/image-only PDFs, unlike plain text extraction.</li>
 * </ul>
 */
@Component
public class DocumentToImagesConverter {

    private static final Logger log = LoggerFactory.getLogger(DocumentToImagesConverter.class);
    private static final MimeType PNG = MimeType.valueOf(MediaType.IMAGE_PNG_VALUE);

    private final DocumentProperties properties;

    public DocumentToImagesConverter(DocumentProperties properties) {
        this.properties = properties;
    }

    /** True if the upload is a content type this converter knows how to handle. */
    public boolean isSupported(String contentType) {
        return contentType != null
                && (contentType.startsWith("image/") || isPdf(contentType));
    }

    /**
     * @param file an image or PDF upload
     * @return one media item per image, or one per rendered PDF page
     */
    public List<Media> toImages(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        if (isPdf(contentType) || isPdfFilename(file.getOriginalFilename())) {
            return renderPdf(file.getBytes());
        }
        // Plain image: pass through unchanged.
        MimeType mimeType = (contentType == null || contentType.isBlank())
                ? PNG
                : MimeType.valueOf(contentType);
        return List.of(new Media(mimeType, new ByteArrayResource(file.getBytes())));
    }

    private List<Media> renderPdf(byte[] pdfBytes) throws IOException {
        List<Media> images = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int total = document.getNumberOfPages();
            int pagesToRender = Math.min(total, properties.maxPages());
            if (total > pagesToRender) {
                log.warn("PDF has {} pages; rendering only the first {} (vision-agent.document.max-pages)",
                        total, pagesToRender);
            }

            PDFRenderer renderer = new PDFRenderer(document);
            for (int page = 0; page < pagesToRender; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, properties.pdfDpi(), ImageType.RGB);
                images.add(new Media(PNG, new ByteArrayResource(toPng(image))));
            }
            log.debug("Rendered {} PDF page(s) at {} DPI", images.size(), properties.pdfDpi());
        }
        return images;
    }

    private byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private boolean isPdf(String contentType) {
        return contentType != null
                && (contentType.equalsIgnoreCase(MediaType.APPLICATION_PDF_VALUE)
                || contentType.equalsIgnoreCase("application/x-pdf"));
    }

    private boolean isPdfFilename(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }
}
