package com.example.invoiceagent.service;

import java.util.UUID;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.example.invoiceagent.api.ClientNotificationResult;
import com.example.invoiceagent.config.InvoiceAgentProperties;
import com.example.invoiceagent.domain.PaymentDecision;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClientNotificationClient {

    private final RestClient restClient;
    private final InvoiceAgentProperties properties;

    public ClientNotificationResult notifyClient(UUID processId, String callbackUrl, PaymentDecision decision, String message) {
        String targetUrl = StringUtils.hasText(callbackUrl) ? callbackUrl : properties.client().defaultCallbackUrl();
        if (properties.client().dryRun() || !StringUtils.hasText(targetUrl)) {
            return new ClientNotificationResult(true, "DRY_RUN", "Client notification skipped: " + decision);
        }

        ClientNotificationRequest request = new ClientNotificationRequest(processId.toString(), decision, message);
        HttpStatusCode statusCode = restClient.post()
                .uri(targetUrl)
                .body(request)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
        return new ClientNotificationResult(statusCode.is2xxSuccessful(), statusCode.toString(), message);
    }

    record ClientNotificationRequest(String processId, PaymentDecision decision, String message) {
    }
}
