package com.example.invoice.agent;

import com.example.invoice.domain.ExtractedInvoice;
import com.example.invoice.domain.GenevaPostResult;
import com.example.invoice.domain.InvoiceContext;
import com.example.invoice.domain.ReviewDecision;
import com.example.invoice.domain.ReviewResult;
import com.example.invoice.domain.ValidationResult;
import com.example.invoice.integrations.GenevaClient;
import org.springframework.stereotype.Component;

@Component
public class GenevaPostAndIntegrationAgent implements InvoiceAgent<GenevaPostAndIntegrationAgent.GenevaInput, GenevaPostResult> {
    public record GenevaInput(ExtractedInvoice invoice, ValidationResult validation, ReviewResult review) { }

    private final GenevaClient genevaClient;

    public GenevaPostAndIntegrationAgent(GenevaClient genevaClient) {
        this.genevaClient = genevaClient;
    }

    @Override
    public String name() {
        return "4. Post & Integration Agent";
    }

    @Override
    public GenevaPostResult run(GenevaInput input, InvoiceContext ctx) {
        if (input.review().decision() != ReviewDecision.APPROVED) {
            GenevaPostResult result = new GenevaPostResult(false, null, "Skipped: review decision " + input.review().decision(), null);
            ctx.record(name(), "post", result.message());
            return result;
        }
        GenevaPostResult result = genevaClient.postInvoice(ctx.invoiceId(), input.invoice(), input.validation());
        ctx.record(name(), "post", "posted=" + result.posted() + "; reference=" + result.downstreamReference());
        return result;
    }
}
