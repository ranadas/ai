package com.agent.liquidalts.invoiceagent

import com.agent.liquidalts.invoiceagent.config.InvoiceAgentProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(InvoiceAgentProperties::class)
class InvoiceAgentApplication

fun main(args: Array<String>) {
    runApplication<InvoiceAgentApplication>(*args)
}
