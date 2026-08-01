package com.example.invoice.agent;

import com.example.invoice.domain.GenevaPostResult;
import com.example.invoice.domain.InvoiceContext;
import com.example.invoice.domain.NavControlPack;
import com.example.invoice.domain.ValidationCheck;
import com.example.invoice.domain.ValidationResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class NavControlPackAgent implements InvoiceAgent<NavControlPackAgent.NavInput, NavControlPack> {
    public record NavInput(ValidationResult validation, GenevaPostResult geneva) { }

    @Override
    public String name() {
        return "5a. NAV Control Pack Agent";
    }

    @Override
    public NavControlPack run(NavInput input, InvoiceContext ctx) {
        Map<String, String> checksSummary = input.validation().checks().stream()
                .collect(Collectors.toMap(
                        ValidationCheck::name,
                        check -> check.passed() ? "PASS" : "FAIL: " + check.message()
                ));
        NavControlPack pack = new NavControlPack(
                "NAV-" + UUID.randomUUID(),
                ctx.invoiceId(),
                Instant.now(),
                input.geneva().posted() ? "READY" : "BLOCKED",
                checksSummary,
                input.geneva().downstreamReference()
        );
        ctx.record(name(), "control-pack", "status=" + pack.status() + "; id=" + pack.controlPackId());
        return pack;
    }
}
