package com.example.invoice.api;

import com.example.invoice.agent.InvoiceProcessingOrchestrator;
import com.example.invoice.domain.InvoiceProcessRequest;
import com.example.invoice.domain.InvoiceProcessResponse;
import com.example.invoice.store.InvoiceMemoryStore;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {
    private final InvoiceProcessingOrchestrator orchestrator;
    private final InvoiceMemoryStore memoryStore;

    public InvoiceController(InvoiceProcessingOrchestrator orchestrator, InvoiceMemoryStore memoryStore) {
        this.orchestrator = orchestrator;
        this.memoryStore = memoryStore;
    }

    @PostMapping(value = "/process", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public InvoiceProcessResponse processJson(@Valid @RequestBody InvoiceProcessRequest request) {
        return orchestrator.process(request);
    }

    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public InvoiceProcessResponse processPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "clientCallbackUrl", required = false) String clientCallbackUrl,
            @RequestParam(value = "source", required = false, defaultValue = "REST_MULTIPART") String source
    ) throws Exception {
        InvoiceProcessRequest request = new InvoiceProcessRequest(
                source,
                file.getOriginalFilename(),
                file.getContentType(),
                Base64.getEncoder().encodeToString(file.getBytes()),
                null,
                clientCallbackUrl,
                Map.of()
        );
        return orchestrator.process(request);
    }

    @GetMapping(value = "/{invoiceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InvoiceProcessResponse> get(@PathVariable String invoiceId) {
        return memoryStore.find(invoiceId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
