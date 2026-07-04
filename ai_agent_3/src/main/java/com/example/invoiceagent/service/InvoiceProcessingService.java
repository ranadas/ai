package com.example.invoiceagent.service;

import java.io.IOException;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.invoiceagent.agent.InvoiceKoogAgents;
import com.example.invoiceagent.api.ClientNotificationResult;
import com.example.invoiceagent.api.FourEyeReview;
import com.example.invoiceagent.api.GenevaUpdateResult;
import com.example.invoiceagent.api.InvoiceExtraction;
import com.example.invoiceagent.api.InvoiceStatusResponse;
import com.example.invoiceagent.api.NavControlPackResult;
import com.example.invoiceagent.api.ProcessInvoiceResponse;
import com.example.invoiceagent.config.InvoiceAgentProperties;
import com.example.invoiceagent.domain.AuditEvent;
import com.example.invoiceagent.domain.InvoiceProcess;
import com.example.invoiceagent.domain.PaymentDecision;
import com.example.invoiceagent.domain.ProcessStatus;
import com.example.invoiceagent.domain.ReviewDecision;
import com.example.invoiceagent.pdf.PdfTextExtractor;
import com.example.invoiceagent.repository.AuditEventRepository;
import com.example.invoiceagent.repository.InvoiceProcessRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InvoiceProcessingService {

    private final InvoiceAgentProperties properties;
    private final InvoiceProcessRepository processRepository;
    private final AuditEventRepository auditEventRepository;
    private final PdfTextExtractor pdfTextExtractor;
    private final InvoiceKoogAgents agents;
    private final GenevaClient genevaClient;
    private final NavControlPackService navControlPackService;
    private final ClientNotificationClient clientNotificationClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProcessInvoiceResponse process(String clientId, String invoiceReference, String callbackUrl, MultipartFile invoice) {
        InvoiceProcess process = createProcess(clientId, invoiceReference, invoice);
        audit(process.getId(), "receive", "OK", "Invoice received");

        InvoiceExtraction extraction = null;
        FourEyeReview review = null;
        GenevaUpdateResult genevaResult = null;
        NavControlPackResult navResult = null;
        ClientNotificationResult notificationResult = null;

        try {
            byte[] pdfBytes = readAndValidate(invoice);
            String invoiceText = pdfTextExtractor.extract(pdfBytes);

            extraction = agents.extractInvoice(clientId, invoiceReference, invoiceText);
            applyExtraction(process, extraction);
            process.setStatus(ProcessStatus.EXTRACTED);
            process.setExtractedJson(writeJson(extraction));
            audit(process.getId(), "manual-review-agent", "OK", "Invoice values extracted by LLM agent");

            review = agents.reviewInvoice(extraction, invoiceText);
            process.setStatus(ProcessStatus.REVIEWED);
            process.setReviewJson(writeJson(review));
            audit(process.getId(), "four-eye-agent", review.decision().name(), review.rationale());

            if (review.decision() != ReviewDecision.APPROVED) {
                String message = "Payment rejected by four-eye review: " + review.rationale();
                notificationResult = clientNotificationClient.notifyClient(process.getId(), callbackUrl, PaymentDecision.REJECT, message);
                process.setStatus(ProcessStatus.PAYMENT_REJECTED);
                process.setClientCallbackStatus(notificationResult.statusCode());
                process.setMessage(message);
                return response(process, PaymentDecision.REJECT, extraction, review, null, null, notificationResult);
            }

            genevaResult = genevaClient.updateInvoice(process.getId(), clientId, extraction);
            audit(process.getId(), "geneva-update", genevaResult.success() ? "OK" : "FAILED", genevaResult.message());
            if (!genevaResult.success()) {
                String message = "Payment rejected because Geneva update failed: " + genevaResult.message();
                notificationResult = clientNotificationClient.notifyClient(process.getId(), callbackUrl, PaymentDecision.REJECT, message);
                process.setStatus(ProcessStatus.FAILED);
                process.setClientCallbackStatus(notificationResult.statusCode());
                process.setMessage(message);
                return response(process, PaymentDecision.REJECT, extraction, review, genevaResult, null, notificationResult);
            }
            process.setStatus(ProcessStatus.GENEVA_UPDATED);
            process.setGenevaCorrelationId(genevaResult.correlationId());

            navResult = navControlPackService.create(process.getId(), clientId, extraction, review);
            audit(process.getId(), "nav-control-pack", navResult.success() ? "OK" : "FAILED", navResult.message());
            if (!navResult.success()) {
                String message = "Payment rejected because NAV control pack creation failed: " + navResult.message();
                notificationResult = clientNotificationClient.notifyClient(process.getId(), callbackUrl, PaymentDecision.REJECT, message);
                process.setStatus(ProcessStatus.FAILED);
                process.setClientCallbackStatus(notificationResult.statusCode());
                process.setMessage(message);
                return response(process, PaymentDecision.REJECT, extraction, review, genevaResult, navResult, notificationResult);
            }
            process.setStatus(ProcessStatus.NAV_CONTROL_PACK_CREATED);
            process.setNavControlPackLocation(navResult.location());

            String message = "Payment confirmed";
            notificationResult = clientNotificationClient.notifyClient(process.getId(), callbackUrl, PaymentDecision.CONFIRM, message);
            audit(process.getId(), "client-response", notificationResult.success() ? "OK" : "FAILED", notificationResult.message());
            process.setClientCallbackStatus(notificationResult.statusCode());
            process.setStatus(notificationResult.success() ? ProcessStatus.PAYMENT_CONFIRMED : ProcessStatus.CLIENT_RESPONDED);
            process.setMessage(message);
            return response(process, PaymentDecision.CONFIRM, extraction, review, genevaResult, navResult, notificationResult);
        } catch (Exception ex) {
            String message = "Payment rejected because processing failed: " + ex.getMessage();
            notificationResult = clientNotificationClient.notifyClient(process.getId(), callbackUrl, PaymentDecision.REJECT, message);
            process.setStatus(ProcessStatus.FAILED);
            process.setClientCallbackStatus(notificationResult.statusCode());
            process.setMessage(message);
            audit(process.getId(), "processing", "FAILED", message);
            return response(process, PaymentDecision.REJECT, extraction, review, genevaResult, navResult, notificationResult);
        }
    }

    @Transactional(readOnly = true)
    public InvoiceStatusResponse getStatus(UUID processId) {
        InvoiceProcess process = processRepository.findById(processId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice process not found: " + processId));
        return InvoiceStatusResponse.from(process, auditEventRepository.findByProcessIdOrderByCreatedAtAsc(processId));
    }

    private InvoiceProcess createProcess(String clientId, String invoiceReference, MultipartFile invoice) {
        InvoiceProcess process = new InvoiceProcess();
        process.setClientId(clientId);
        process.setInvoiceReference(invoiceReference);
        process.setOriginalFilename(invoice.getOriginalFilename());
        process.setContentType(invoice.getContentType());
        process.setStatus(ProcessStatus.RECEIVED);
        return processRepository.save(process);
    }

    private byte[] readAndValidate(MultipartFile invoice) throws IOException {
        if (invoice.isEmpty()) {
            throw new IllegalArgumentException("Invoice PDF is empty");
        }
        if (invoice.getSize() > properties.maxPdfBytes()) {
            throw new IllegalArgumentException("Invoice PDF exceeds max size of " + properties.maxPdfBytes() + " bytes");
        }
        return invoice.getBytes();
    }

    private void applyExtraction(InvoiceProcess process, InvoiceExtraction extraction) {
        process.setInvoiceNumber(extraction.invoiceNumber());
        process.setSupplierName(extraction.supplierName());
        process.setCurrency(extraction.currency());
        process.setAmount(extraction.amount());
        process.setDueDate(extraction.dueDate());
    }

    private ProcessInvoiceResponse response(
            InvoiceProcess process,
            PaymentDecision paymentDecision,
            InvoiceExtraction extraction,
            FourEyeReview review,
            GenevaUpdateResult genevaResult,
            NavControlPackResult navResult,
            ClientNotificationResult notificationResult
    ) {
        return new ProcessInvoiceResponse(
                process.getId(),
                process.getStatus(),
                paymentDecision,
                process.getMessage(),
                extraction,
                review,
                genevaResult,
                navResult,
                notificationResult
        );
    }

    private void audit(UUID processId, String step, String status, String message) {
        AuditEvent event = new AuditEvent();
        event.setProcessId(processId);
        event.setStep(step);
        event.setStatus(status);
        event.setMessage(message);
        auditEventRepository.save(event);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Could not serialize process payload", ex);
        }
    }
}
