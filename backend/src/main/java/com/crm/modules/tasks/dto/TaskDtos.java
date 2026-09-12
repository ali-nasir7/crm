package com.crm.modules.tasks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class TaskDtos {
    private TaskDtos() {}

    public record CreateTaskRequest(@NotBlank @Size(max = 160) String title, @Size(max = 5000) String description,
                                    UUID leadId, UUID companyId, UUID contactId, String taskType,
                                    UUID assignedUserId, Instant dueAt, String priority) {}

    public record UpdateTaskRequest(@Size(max = 160) String title, @Size(max = 5000) String description,
                                    Instant dueAt, String priority, String status, String completionNote) {}

    public record TaskItem(UUID id, String title, String description, UUID leadId, String businessName,
                           String taskType, UUID assignedUserId, String assignedUserName, UUID createdBy,
                           Instant dueAt, String priority, String status, Instant completedAt,
                           String completionNote, Instant createdAt) {}
}
