package com.example.visionagent.extraction;

import com.example.visionagent.document.DocumentToImagesConverter;
import com.example.visionagent.web.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExtractionController.class)
@Import({ApiExceptionHandler.class, DocumentToImagesConverter.class})
class ExtractionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    VisionExtractionService extractionService;

    @Test
    void returnsExtractedDataPointsForImage() throws Exception {
        when(extractionService.extract(any(), any())).thenReturn(new ExtractionResult(
                "A grocery receipt.",
                "receipt",
                List.of(new ExtractionResult.DataPoint("Total", "12.34", 0.98))));

        var file = new MockMultipartFile("file", "receipt.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/extract").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("receipt"))
                .andExpect(jsonPath("$.dataPoints[0].label").value("Total"))
                .andExpect(jsonPath("$.dataPoints[0].value").value("12.34"));
    }

    @Test
    void acceptsPdfUploadAndRendersPages() throws Exception {
        when(extractionService.extract(any(), any())).thenReturn(new ExtractionResult(
                "An invoice.", "invoice",
                List.of(new ExtractionResult.DataPoint("Invoice No", "INV-1", 0.9))));

        var pdf = new MockMultipartFile("file", "invoice.pdf", "application/pdf", onePagePdf());

        mockMvc.perform(multipart("/api/v1/extract").file(pdf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("invoice"))
                .andExpect(jsonPath("$.dataPoints[0].label").value("Invoice No"));
    }

    @Test
    void rejectsUnsupportedUpload() throws Exception {
        var file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/v1/extract").file(file))
                .andExpect(status().isUnsupportedMediaType());
    }

    /** Minimal valid single-page PDF so PDFBox can render it during the test. */
    private static byte[] onePagePdf() {
        String pdf = """
                %PDF-1.4
                1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
                2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
                3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj
                xref
                0 4
                0000000000 65535 f
                0000000009 00000 n
                0000000052 00000 n
                0000000101 00000 n
                trailer<</Size 4/Root 1 0 R>>
                startxref
                164
                %%EOF
                """;
        return pdf.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
}
