package com.agent.liquidalts.invoiceagent.api

import com.agent.liquidalts.invoiceagent.audit.AuditEvent
import com.agent.liquidalts.invoiceagent.audit.AuditTrail
import com.agent.liquidalts.invoiceagent.service.InvoiceNotFoundException
import com.agent.liquidalts.invoiceagent.service.InvoiceProcessingService
import com.agent.liquidalts.invoiceagent.service.ReviewService
import com.agent.liquidalts.invoiceagent.store.InvoiceStore
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.util.UriComponentsBuilder
import java.util.UUID

/**
 * Public API for the invoice pipeline.
 *
 * Submission follows the async 202 pattern: the PDF is accepted, an id is
 * returned immediately, and the agent pipeline runs in the background.
 * In production `X-Submitted-By` comes from the OAuth2 principal, not a header.
 */
@RestController
@RequestMapping("/api/v1/invoices")
class InvoiceController(
    private val processing: InvoiceProcessingService,
    private val reviews: ReviewService,
    private val store: InvoiceStore,
    private val audit: AuditTrail,
) {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun submit(
        @RequestPart("file") file: MultipartFile,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestHeader("X-Submitted-By") submittedBy: String,
        uriBuilder: UriComponentsBuilder,
    ): ResponseEntity<SubmissionResponse> {
        val key = idempotencyKey ?: UUID.randomUUID().toString()
        val existing = store.findByIdempotencyKey(key)
        val record = processing.submit(
            idempotencyKey = key,
            submittedBy = submittedBy,
            fileName = file.originalFilename ?: "invoice.pdf",
            pdfBytes = file.bytes,
        )
        val location = uriBuilder.path("/api/v1/invoices/{id}").build(record.id)
        return ResponseEntity.accepted()
            .location(location)
            .body(SubmissionResponse(record.id, record.status, duplicate = existing != null))
    }

    @GetMapping("/{id}")
    fun status(@PathVariable id: UUID): InvoiceView =
        InvoiceView.from(store.findById(id) ?: throw InvoiceNotFoundException(id))

    /** Four-eye review capture — the human-in-the-loop gate. */
    @PostMapping("/{id}/review")
    fun review(@PathVariable id: UUID, @Valid @RequestBody request: ReviewRequest): InvoiceView =
        InvoiceView.from(reviews.decide(id, request.reviewer, request.decision, request.comment))

    @GetMapping("/{id}/audit")
    fun auditTrail(@PathVariable id: UUID): List<AuditEvent> {
        store.findById(id) ?: throw InvoiceNotFoundException(id)
        return audit.forInvoice(id)
    }

    @GetMapping("/{id}/control-pack", produces = [MediaType.TEXT_MARKDOWN_VALUE])
    fun controlPack(@PathVariable id: UUID): ResponseEntity<String> {
        val record = store.findById(id) ?: throw InvoiceNotFoundException(id)
        return record.controlPack
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }
}
