package com.agent.liquidalts.invoiceagent.api

import com.agent.liquidalts.invoiceagent.service.IllegalReviewStateException
import com.agent.liquidalts.invoiceagent.service.InvoiceNotFoundException
import com.agent.liquidalts.invoiceagent.service.SegregationOfDutiesException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

/** RFC 7807 problem details for all API errors — house convention. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(InvoiceNotFoundException::class)
    fun notFound(e: InvoiceNotFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "invoice-not-found", "Invoice not found", e.message)

    @ExceptionHandler(IllegalReviewStateException::class)
    fun conflict(e: IllegalReviewStateException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "invalid-review-state", "Invoice is not reviewable", e.message)

    @ExceptionHandler(SegregationOfDutiesException::class)
    fun forbidden(e: SegregationOfDutiesException): ProblemDetail =
        problem(HttpStatus.FORBIDDEN, "segregation-of-duties", "Four-eye principle violated", e.message)

    private fun problem(status: HttpStatus, type: String, title: String, detail: String?): ProblemDetail =
        ProblemDetail.forStatus(status).apply {
            this.type = URI.create("https://api.agent-liquidalts.example/problems/$type")
            this.title = title
            this.detail = detail
        }
}
