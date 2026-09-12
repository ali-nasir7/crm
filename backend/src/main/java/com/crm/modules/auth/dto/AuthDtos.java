package com.crm.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    public record CompleteOnboardingRequest(@NotBlank @Email String email,
                                            @NotBlank String tempPassword,
                                            @NotBlank String newPassword) {}

    public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds, UserInfo user) {}

    public record UserInfo(UUID id, String email, String firstName, String lastName, String displayName,
                           boolean superAdmin, Set<String> roles, Set<String> permissions,
                           Map<String, Integer> dailyTargets, String organizationName) {}

    public record MeResponse(UUID id, String email, String firstName, String lastName, String displayName,
                             boolean superAdmin, Set<String> roles, Set<String> permissions,
                             Map<String, Integer> dailyTargets, String organizationName, UUID organizationId) {}
}
