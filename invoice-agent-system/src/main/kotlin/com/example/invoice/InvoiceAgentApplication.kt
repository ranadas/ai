package com.example.invoice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class InvoiceAgentApplication

fun main(args: Array<String>) {
    runApplication<InvoiceAgentApplication>(*args)
}
