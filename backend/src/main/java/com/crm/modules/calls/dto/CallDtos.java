package com.crm.modules.calls.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public final class CallDtos {
    private CallDtos() {}

    public record CreateCallRequest(
        @NotBlank String outcome, String direction, Integer durationSeconds,
        String notes, String nextAction, Instant followUpAt, Instant occurredAt) {}

    public record CallItem(UUID id, UUID leadId, String businessName, UUID userId, String userName,
                           String direction, Instant occurredAt, Integer durationSeconds, String outcome,
                           String notes, String nextAction, Instant followUpAt, Instant createdAt) {}
}
