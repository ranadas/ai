package com.agent.liquidalts.invoiceagent.api;

import com.agent.liquidalts.invoiceagent.audit.AuditEvent;
import com.agent.liquidalts.invoiceagent.audit.AuditTrail;
import com.agent.liquidalts.invoiceagent.domain.InvoiceRecord;
import com.agent.liquidalts.invoiceagent.service.InvoiceNotFoundException;
import com.agent.liquidalts.invoiceagent.service.InvoiceProcessingService;
import com.agent.liquidalts.invoiceagent.service.ReviewService;
import com.agent.liquidalts.invoiceagent.store.InvoiceStore;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Public API for the invoice pipeline.
 *
 * Submission follows the async 202 pattern: the PDF is accepted, an id is
 * returned immediately, and the agent pipeline runs in the background.
 * In production {@code X-Submitted-By} comes from the OAuth2 principal,
 * not a header.
 */
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceProcessingService processing;
    private final ReviewService reviews;
    private final InvoiceStore store;
    private final AuditTrail audit;

    public InvoiceController(InvoiceProcessingService processing,
                             ReviewService reviews,
                             InvoiceStore store,
                             AuditTrail audit) {
        this.processing = processing;
        this.reviews = reviews;
        this.store = store;
        this.audit = audit;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmissionResponse> submit(
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader("X-Submitted-By") String submittedBy,
            UriComponentsBuilder uriBuilder) {

        String key = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();
        boolean duplicate = store.findByIdempotencyKey(key).isPresent();

        InvoiceRecord record = processing.submit(key, submittedBy, fileNameOf(file), bytesOf(file));

        URI location = uriBuilder.path("/api/v1/invoices/{id}").build(record.id());
        return ResponseEntity.accepted()
                .location(location)
                .body(new SubmissionResponse(record.id(), record.status(), duplicate));
    }

    @GetMapping("/{id}")
    public InvoiceView status(@PathVariable UUID id) {
        return InvoiceView.from(require(id));
    }

    /** Four-eye review capture — the human-in-the-loop gate. */
    @PostMapping("/{id}/review")
    public InvoiceView review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        return InvoiceView.from(
                reviews.decide(id, request.reviewer(), request.decision(), request.comment()));
    }

    @GetMapping("/{id}/audit")
    public List<AuditEvent> auditTrail(@PathVariable UUID id) {
        require(id);
        return audit.forInvoice(id);
    }

    @GetMapping(value = "/{id}/control-pack", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public ResponseEntity<String> controlPack(@PathVariable UUID id) {
        InvoiceRecord record = require(id);
        return record.controlPack() != null
                ? ResponseEntity.ok(record.controlPack())
                : ResponseEntity.notFound().build();
    }

    private InvoiceRecord require(UUID id) {
        return store.findById(id).orElseThrow(() -> new InvoiceNotFoundException(id));
    }

    private static String fileNameOf(MultipartFile file) {
        return file.getOriginalFilename() != null ? file.getOriginalFilename() : "invoice.pdf";
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
    }
}
