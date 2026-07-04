package com.agent.liquidalts.invoiceagent.integration

import com.agent.liquidalts.invoiceagent.config.InvoiceAgentProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.UUID

enum class Outcome { SUCCESS, FAILURE }

data class ClientNotification(
    val invoiceId: UUID,
    val invoiceNumber: String?,
    val outcome: Outcome,
    val message: String,
)

/**
 * Sends the final success/failure response to the client
 * (payment confirmed vs. rejected with reason & action required).
 */
@Component
class ClientNotifier(props: InvoiceAgentProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val client: RestClient = RestClient.builder()
        .baseUrl(props.notification.clientEndpoint)
        .build()

    fun notify(notification: ClientNotification) {
        log.info("Notifying client: invoice={} outcome={}", notification.invoiceId, notification.outcome)
        runCatching {
            client.post().body(notification).retrieve().toBodilessEntity()
        }.onFailure {
            // Notification failure must never roll back a successful posting;
            // in production this lands on a retry queue (Kafka/SQS + DLQ).
            log.warn("Client notification failed for invoice {}: {}", notification.invoiceId, it.message)
        }
    }
}
