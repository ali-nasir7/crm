package com.crm.modules.leads.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LeadDtos {
    private LeadDtos() {}

    public record CreateLeadRequest(
        @NotBlank @Size(max = 160) String businessName,
        @Size(max = 80) String firstName, @Size(max = 80) String lastName, @Size(max = 80) String jobTitle,
        @jakarta.validation.constraints.Email @Size(max = 255) String email,
        @Size(max = 255) String secondaryEmail, @Size(max = 32) String phone, @Size(max = 32) String whatsapp,
        @Size(max = 255) String website, @Size(max = 255) String linkedin,
        @Size(max = 64) String country, @Size(max = 64) String state, @Size(max = 64) String city,
        @Size(max = 255) String address, @Size(max = 64) String timezone,
        @Size(max = 80) String industry, @Size(max = 64) String businessType,
        @Size(max = 32) String companySize, Integer employeesCount, @Size(max = 32) String revenueRange,
        Map<String, Object> customFields,
        String status, UUID sourceId, UUID pipelineId, UUID stageId, UUID assignedUserId,
        Instant nextFollowUpAt, @Size(max = 2000) String notes, List<String> tags) {}

    public record UpdateLeadRequest(
        @Size(max = 160) String businessName,
        @Size(max = 80) String firstName, @Size(max = 80) String lastName, @Size(max = 80) String jobTitle,
        @jakarta.validation.constraints.Email @Size(max = 255) String email,
        @Size(max = 255) String secondaryEmail, @Size(max = 32) String phone, @Size(max = 32) String whatsapp,
        @Size(max = 255) String website, @Size(max = 255) String linkedin,
        @Size(max = 64) String country, @Size(max = 64) String state, @Size(max = 64) String city,
        @Size(max = 255) String address, @Size(max = 64) String timezone,
        @Size(max = 80) String industry, @Size(max = 64) String businessType,
        @Size(max = 32) String companySize, Integer employeesCount, @Size(max = 32) String revenueRange,
        Map<String, Object> customFields,
        String status, UUID sourceId, Instant nextFollowUpAt,
        @Size(max = 2000) String notes, List<String> tags) {}

    public record AssignRequest(UUID userId) {}
    public record StageRequest(UUID stageId) {}
    public record StatusRequest(String status) {}
    public record TagsRequest(List<String> tags) {}

    public record LeadItem(UUID id, String businessName, String firstName, String lastName, String contactName,
                           String jobTitle, String email, String phone, String whatsapp, String website, String linkedin,
                           String country, String state, String city, String address, String timezone,
                           String industry, String businessType, String companySize, Integer employeesCount,
                           String revenueRange, Map<String, Object> customFields,
                           String status, int score, String scoreCategory,
                           UUID sourceId, String sourceName,
                           UUID pipelineId, UUID stageId, String stageName,
                           UUID assignedUserId, String assignedUserName,
                           Instant lastContactedAt, Instant nextFollowUpAt,
                           List<String> tags, String notes,
                           UUID companyId, UUID contactId,
                           Instant createdAt, Instant updatedAt) {}

    public record SavedViewRequest(@NotBlank @Size(max = 80) String name, boolean shared,
                                   Map<String, Object> filters, String sort) {}

    public record ScoringRuleRequest(String criterion, String operand, int points, String label, boolean active) {}

    public record CustomFieldRequest(@NotBlank String key, @NotBlank String label, @NotBlank String type,
                                     List<String> options, int position) {}

    public record BulkLeadRequest(String action, List<UUID> leadIds, Map<String, Object> filters,
                                  UUID userId, UUID stageId, String status, List<String> tags) {}

    public record ConvertLeadRequest(UUID dealStageId, Double amount, String currency, String clientStatus,
                                     @Size(max = 255) String notes) {}
}
