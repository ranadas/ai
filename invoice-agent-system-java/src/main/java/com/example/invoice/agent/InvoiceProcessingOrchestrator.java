package com.example.invoice.agent;

import com.example.invoice.domain.ClientNotificationResult;
import com.example.invoice.domain.ExtractedInvoice;
import com.example.invoice.domain.GenevaPostResult;
import com.example.invoice.domain.InvoiceContext;
import com.example.invoice.domain.InvoiceProcessRequest;
import com.example.invoice.domain.InvoiceProcessResponse;
import com.example.invoice.domain.NavControlPack;
import com.example.invoice.domain.ProcessingStatus;
import com.example.invoice.domain.ReviewDecision;
import com.example.invoice.domain.ReviewResult;
import com.example.invoice.domain.ValidationResult;
import com.example.invoice.store.InvoiceMemoryStore;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

@Service
public class InvoiceProcessingOrchestrator {
    private final InvoiceIngestAndUnderstandAgent ingestAgent;
    private final DataValidationAndEnrichmentAgent validationAgent;
    private final FourEyeReviewAgent fourEyeReviewAgent;
    private final GenevaPostAndIntegrationAgent genevaAgent;
    private final NavControlPackAgent navControlPackAgent;
    private final ClientNotificationAgent clientNotificationAgent;
    private final InvoiceMemoryStore memoryStore;

    public InvoiceProcessingOrchestrator(
            InvoiceIngestAndUnderstandAgent ingestAgent,
            DataValidationAndEnrichmentAgent validationAgent,
            FourEyeReviewAgent fourEyeReviewAgent,
            GenevaPostAndIntegrationAgent genevaAgent,
            NavControlPackAgent navControlPackAgent,
            ClientNotificationAgent clientNotificationAgent,
            InvoiceMemoryStore memoryStore
    ) {
        this.ingestAgent = ingestAgent;
        this.validationAgent = validationAgent;
        this.fourEyeReviewAgent = fourEyeReviewAgent;
        this.genevaAgent = genevaAgent;
        this.navControlPackAgent = navControlPackAgent;
        this.clientNotificationAgent = clientNotificationAgent;
        this.memoryStore = memoryStore;
    }

    public InvoiceProcessResponse process(InvoiceProcessRequest request) {
        InvoiceContext ctx = new InvoiceContext(
                request.source(),
                request.fileName(),
                request.contentType(),
                decode(request.pdfBase64()),
                request.manualText(),
                request.clientCallbackUrl(),
                request.metadata()
        );
        ctx.record("AI Agent Orchestration Layer", "start", "Received invoice from " + request.source());

        try {
            ExtractedInvoice extracted = ingestAgent.run(request, ctx);
            ValidationResult validation = validationAgent.run(extracted, ctx);
            ReviewResult review = fourEyeReviewAgent.run(new FourEyeReviewAgent.ReviewInput(extracted, validation), ctx);
            GenevaPostResult geneva = genevaAgent.run(new GenevaPostAndIntegrationAgent.GenevaInput(extracted, validation, review), ctx);
            NavControlPack nav = navControlPackAgent.run(new NavControlPackAgent.NavInput(validation, geneva), ctx);

            ProcessingStatus status;
            if (geneva.posted()) {
                status = ProcessingStatus.CONTROL_PACK_CREATED;
            } else if (review.decision() == ReviewDecision.NEEDS_MANUAL_REVIEW) {
                status = ProcessingStatus.REVIEW_REQUIRED;
            } else {
                status = ProcessingStatus.FAILED;
            }

            List<String> errors = geneva.posted() || geneva.message() == null ? List.of() : List.of(geneva.message());
            InvoiceProcessResponse preliminary = new InvoiceProcessResponse(
                    ctx.invoiceId(),
                    status,
                    extracted,
                    validation,
                    review,
                    geneva,
                    nav,
                    null,
                    errors,
                    List.of()
            );

            ClientNotificationResult notification = clientNotificationAgent.run(preliminary, ctx);
            InvoiceProcessResponse finalResponse = preliminary.withClientNotification(notification, ctx.audit());
            return memoryStore.save(finalResponse);
        } catch (Exception ex) {
            ctx.record("AI Agent Orchestration Layer", "fail", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            return memoryStore.save(new InvoiceProcessResponse(
                    ctx.invoiceId(),
                    ProcessingStatus.FAILED,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(ex.getMessage() == null ? "Unknown processing failure" : ex.getMessage()),
                    ctx.audit()
            ));
        }
    }

    private static byte[] decode(String pdfBase64) {
        return pdfBase64 == null || pdfBase64.isBlank() ? null : Base64.getDecoder().decode(pdfBase64);
    }
}
