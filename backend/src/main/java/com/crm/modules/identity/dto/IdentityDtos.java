package com.crm.modules.identity.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class IdentityDtos {
    private IdentityDtos() {}

    // ---- Users ----
    public record CreateUserRequest(
        @NotBlank @Email String email, String password,
        @NotBlank @Size(max = 80) String firstName, @NotBlank @Size(max = 80) String lastName,
        @Size(max = 80) String jobTitle, @Size(max = 32) String phone,
        List<String> roleKeys, List<UUID> teamIds, Map<String, Integer> dailyTargets) {}

    public record UpdateUserRequest(
        @NotBlank @Size(max = 80) String firstName, @NotBlank @Size(max = 80) String lastName,
        @Size(max = 80) String jobTitle, @Size(max = 32) String phone, String status,
        List<String> roleKeys, List<UUID> teamIds, Map<String, Integer> dailyTargets) {}

    public record UserItem(UUID id, String email, String firstName, String lastName, String displayName,
                           String jobTitle, String phone, String status, boolean superAdmin,
                           Set<String> roleKeys, List<TeamSummary> teams, Map<String, Integer> dailyTargets,
                           Instant lastLoginAt, Instant createdAt, String tempPassword) {}

    public record TeamSummary(UUID id, String name) {}

    // ---- Roles ----
    public record RoleRequest(@NotBlank @Size(max = 64) String name, @Size(max = 255) String description,
                              String dataScope, List<String> permissionKeys) {}

    public record RoleItem(UUID id, String key, String name, String description, String dataScope,
                           boolean system, Set<String> permissionKeys, long userCount) {}

    public record PermissionItem(String key, String name, String category) {}

    // ---- Teams ----
    public record TeamRequest(@NotBlank @Size(max = 80) String name, @Size(max = 255) String description, UUID managerId) {}

    public record TeamItem(UUID id, String name, String description, UUID managerId, String managerName,
                           List<UserSummary> members, Instant createdAt) {}

    public record UserSummary(UUID id, String displayName, String email) {}

    public record MemberRequest(List<UUID> userIds) {}
}
