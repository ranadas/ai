package com.example.invoice.agent;

import com.example.invoice.domain.ClientNotificationResult;
import com.example.invoice.domain.InvoiceContext;
import com.example.invoice.domain.InvoiceProcessResponse;
import com.example.invoice.integrations.ClientCallbackClient;
import org.springframework.stereotype.Component;

@Component
public class ClientNotificationAgent implements InvoiceAgent<InvoiceProcessResponse, ClientNotificationResult> {
    private final ClientCallbackClient clientCallbackClient;

    public ClientNotificationAgent(ClientCallbackClient clientCallbackClient) {
        this.clientCallbackClient = clientCallbackClient;
    }

    @Override
    public String name() {
        return "5b. Control Pack & Notification Agent";
    }

    @Override
    public ClientNotificationResult run(InvoiceProcessResponse input, InvoiceContext ctx) {
        ClientNotificationResult result = clientCallbackClient.send(input, ctx.clientCallbackUrl());
        ctx.record(name(), "notify", "sent=" + result.sent() + "; outcome=" + result.outcome());
        return result;
    }
}
