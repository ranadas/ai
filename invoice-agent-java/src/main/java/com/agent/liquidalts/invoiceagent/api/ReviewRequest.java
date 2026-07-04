package com.agent.liquidalts.invoiceagent.api;

import com.agent.liquidalts.invoiceagent.domain.Decision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReviewRequest(
        @NotBlank String reviewer,
        @NotNull Decision decision,
        String comment
) {}
