package com.crm.modules.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class ActivityDtos {
    private ActivityDtos() {}

    public record CreateNoteRequest(@NotBlank @Size(max = 10000) String body) {}

    public record ActivityItem(UUID id, String type, UUID leadId, UUID actorId, String actorName,
                               String subject, String body, Map<String, Object> metadata,
                               Instant occurredAt, Instant createdAt) {}
}
