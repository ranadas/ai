package com.example.invoiceagent.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.invoiceagent.service.InvoiceProcessingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/invoices")
@Validated
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceProcessingService invoiceProcessingService;

    @PostMapping(path = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProcessInvoiceResponse process(
            @RequestParam @NotBlank String clientId,
            @RequestParam(required = false) String invoiceReference,
            @RequestParam(required = false) String callbackUrl,
            @RequestParam MultipartFile invoice
    ) {
        return invoiceProcessingService.process(clientId, invoiceReference, callbackUrl, invoice);
    }

    @GetMapping("/{processId}")
    public InvoiceStatusResponse status(@PathVariable UUID processId) {
        return invoiceProcessingService.getStatus(processId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(Instant.now(), 400, "Bad Request", ex.getMessage()));
    }
}
