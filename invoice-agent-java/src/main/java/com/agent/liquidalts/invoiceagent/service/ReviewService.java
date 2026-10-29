package com.agent.liquidalts.invoiceagent.service;

import com.agent.liquidalts.invoiceagent.audit.AuditTrail;
import com.agent.liquidalts.invoiceagent.config.InvoiceAgentProperties;
import com.agent.liquidalts.invoiceagent.domain.Decision;
import com.agent.liquidalts.invoiceagent.domain.InvoiceRecord;
import com.agent.liquidalts.invoiceagent.domain.InvoiceStatus;
import com.agent.liquidalts.invoiceagent.domain.ReviewDecision;
import com.agent.liquidalts.invoiceagent.store.InvoiceStore;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The human-in-the-loop four-eye gate (agent 3 on the diagram captures the
 * approval; this service enforces it). Deliberately NOT an LLM concern:
 * approval authority, segregation of duties and state transitions are
 * enforced in code before any agent is allowed to touch Geneva.
 */
@Service
public class ReviewService {

    private final InvoiceStore store;
    private final InvoicePipeline pipeline;
    private final AuditTrail audit;
    private final InvoiceAgentProperties props;

    public ReviewService(InvoiceStore store,
                         InvoicePipeline pipeline,
                         AuditTrail audit,
                         InvoiceAgentProperties props) {
        this.store = store;
        this.pipeline = pipeline;
        this.audit = audit;
        this.props = props;
    }

    public InvoiceRecord decide(UUID invoiceId, String reviewer, Decision decision, String comment) {
        InvoiceRecord record = store.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        if (record.status() != InvoiceStatus.PENDING_REVIEW) {
            throw new IllegalReviewStateException(
                    "Invoice %s is not awaiting review (status=%s)".formatted(invoiceId, record.status()));
        }
        if (props.review().enforceSegregationOfDuties()
                && reviewer.equalsIgnoreCase(record.submittedBy())) {
            audit.record(invoiceId, reviewer, "SOD_VIOLATION_BLOCKED", "Submitter attempted self-review");
            throw new SegregationOfDutiesException(
                    "Four-eye principle: reviewer must differ from submitter '%s'"
                            .formatted(record.submittedBy()));
        }

        ReviewDecision reviewDecision = ReviewDecision.of(reviewer, decision, comment);
        audit.record(invoiceId, reviewer, "REVIEW_DECISION",
                decision + (comment != null ? " — " + comment : ""));

        return switch (decision) {
            case APPROVE -> {
                InvoiceRecord approved = store.update(invoiceId, r -> r.toBuilder()
                        .reviewDecision(reviewDecision)
                        .status(InvoiceStatus.POSTING)
                        .build());
                pipeline.runPostingPipeline(invoiceId);
                yield approved;
            }
            case REJECT -> {
                String reason = comment != null ? comment : "Rejected by reviewer " + reviewer;
                InvoiceRecord rejected = store.update(invoiceId, r -> r.toBuilder()
                        .reviewDecision(reviewDecision)
                        .status(InvoiceStatus.REJECTED)
                        .failureReason(reason)
                        .build());
                pipeline.runRejectionNotification(invoiceId, reason);
                yield rejected;
            }
        };
    }
}
