package com.agent.liquidalts.invoiceagent.api;

import com.agent.liquidalts.invoiceagent.service.IllegalReviewStateException;
import com.agent.liquidalts.invoiceagent.service.InvoiceNotFoundException;
import com.agent.liquidalts.invoiceagent.service.SegregationOfDutiesException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/** RFC 7807 problem details for all API errors — house convention. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String PROBLEM_BASE = "https://api.agent-liquidalts.example/problems/";

    @ExceptionHandler(InvoiceNotFoundException.class)
    public ProblemDetail notFound(InvoiceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "invoice-not-found", "Invoice not found", e);
    }

    @ExceptionHandler(IllegalReviewStateException.class)
    public ProblemDetail conflict(IllegalReviewStateException e) {
        return problem(HttpStatus.CONFLICT, "invalid-review-state", "Invoice is not reviewable", e);
    }

    @ExceptionHandler(SegregationOfDutiesException.class)
    public ProblemDetail forbidden(SegregationOfDutiesException e) {
        return problem(HttpStatus.FORBIDDEN, "segregation-of-duties", "Four-eye principle violated", e);
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, Exception e) {
        ProblemDetail detail = ProblemDetail.forStatus(status);
        detail.setType(URI.create(PROBLEM_BASE + type));
        detail.setTitle(title);
        detail.setDetail(e.getMessage());
        return detail;
    }
}
