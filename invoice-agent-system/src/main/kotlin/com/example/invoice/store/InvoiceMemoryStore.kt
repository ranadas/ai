package com.example.invoice.store

import com.example.invoice.domain.InvoiceProcessResponse
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class InvoiceMemoryStore {
    private val results = ConcurrentHashMap<String, InvoiceProcessResponse>()

    fun save(response: InvoiceProcessResponse): InvoiceProcessResponse {
        results[response.invoiceId] = response
        return response
    }

    fun find(invoiceId: String): InvoiceProcessResponse? = results[invoiceId]
}
