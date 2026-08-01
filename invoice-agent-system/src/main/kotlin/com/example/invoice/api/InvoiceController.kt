package com.example.invoice.api

import com.example.invoice.agent.InvoiceProcessingOrchestrator
import com.example.invoice.domain.InvoiceProcessRequest
import com.example.invoice.domain.InvoiceProcessResponse
import com.example.invoice.store.InvoiceMemoryStore
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.Base64

@RestController
@RequestMapping("/api/invoices")
class InvoiceController(
    private val orchestrator: InvoiceProcessingOrchestrator,
    private val memoryStore: InvoiceMemoryStore
) {
    @PostMapping("/process", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun processJson(@Valid @RequestBody request: InvoiceProcessRequest): InvoiceProcessResponse =
        orchestrator.process(request)

    @PostMapping("/process", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun processPdf(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("clientCallbackUrl", required = false) clientCallbackUrl: String?,
        @RequestParam("source", required = false, defaultValue = "REST_MULTIPART") source: String
    ): InvoiceProcessResponse = orchestrator.process(
        InvoiceProcessRequest(
            source = source,
            fileName = file.originalFilename,
            contentType = file.contentType,
            pdfBase64 = Base64.getEncoder().encodeToString(file.bytes),
            clientCallbackUrl = clientCallbackUrl
        )
    )

    @GetMapping("/{invoiceId}")
    fun get(@PathVariable invoiceId: String): InvoiceProcessResponse? = memoryStore.find(invoiceId)
}
