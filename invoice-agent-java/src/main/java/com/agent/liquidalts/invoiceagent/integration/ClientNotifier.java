package com.agent.liquidalts.invoiceagent.integration;

import com.agent.liquidalts.invoiceagent.config.InvoiceAgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Sends the final success/failure response to the client
 * (payment confirmed vs. rejected with reason and action required).
 */
@Component
public class ClientNotifier {

    private static final Logger log = LoggerFactory.getLogger(ClientNotifier.class);

    private final RestClient client;

    public ClientNotifier(InvoiceAgentProperties props) {
        this.client = RestClient.builder()
                .baseUrl(props.notification().clientEndpoint())
                .build();
    }

    public void notify(ClientNotification notification) {
        log.info("Notifying client: invoice={} outcome={}", notification.invoiceId(), notification.outcome());
        try {
            client.post().body(notification).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            // Notification failure must never roll back a successful posting;
            // in production this lands on a retry queue (Kafka/SQS + DLQ).
            log.warn("Client notification failed for invoice {}: {}", notification.invoiceId(), e.getMessage());
        }
    }
}
