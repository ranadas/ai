package com.example.visionagent.extraction;

import com.example.visionagent.document.DocumentToImagesConverter;
import org.springframework.ai.content.Media;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/extract")
public class ExtractionController {

    private final VisionExtractionService extractionService;
    private final DocumentToImagesConverter converter;

    public ExtractionController(VisionExtractionService extractionService,
                                DocumentToImagesConverter converter) {
        this.extractionService = extractionService;
        this.converter = converter;
    }

    /**
     * Accepts an image or a PDF and returns the data points extracted by the vision LLM.
     * PDFs are rendered page-by-page to images before being sent to the model.
     *
     * <p>The upload may be supplied as either the {@code file} or the (legacy) {@code image}
     * form field.
     *
     * <pre>
     * curl -F "file=@invoice.pdf" \
     *      -F "instructions=Focus on totals and tax" \
     *      http://localhost:8080/api/v1/extract
     * </pre>
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ExtractionResult extract(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "instructions", required = false) String instructions) {

        MultipartFile upload = (file != null && !file.isEmpty()) ? file : image;

        if (upload == null || upload.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A 'file' (image or PDF) is required.");
        }
        if (!converter.isSupported(upload.getContentType())
                && !isPdfFilename(upload.getOriginalFilename())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Uploaded file must be an image or a PDF, got: " + upload.getContentType());
        }

        try {
            List<Media> images = converter.toImages(upload);
            return extractionService.extract(images, instructions);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not read or render the uploaded file.", e);
        }
    }

    private boolean isPdfFilename(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }
}
