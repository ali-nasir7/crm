package com.crm.modules.email.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EmailDtos {
    private EmailDtos() {}

    public record CreateAccountRequest(String provider, @NotBlank @jakarta.validation.constraints.Email String email,
                                       @Size(max = 120) String displayName, @Size(max = 255) String smtpHost,
                                       Integer smtpPort, String smtpEncryption, String smtpUsername, String smtpPassword,
                                       Integer dailyLimit) {}

    public record AccountItem(UUID id, String provider, String email, String displayName, String smtpHost,
                              Integer smtpPort, String smtpEncryption, String status, Instant verifiedAt,
                              int dailyLimit, UUID userId, Instant createdAt) {}

    public record TemplateRequest(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 255) String subject,
                                  String bodyHtml, String bodyText, String category) {}

    public record TemplateItem(UUID id, String name, String subject, String bodyHtml, String bodyText,
                               String category, boolean active, List<String> variables, Instant createdAt) {}

    public record SendEmailRequest(UUID accountId, @NotBlank String subject, String bodyHtml, String bodyText) {}

    public record EmailItem(UUID id, UUID leadId, UUID accountId, String fromEmail, List<String> toEmails,
                            String subject, String direction, String status, Instant sentAt, Instant openedAt,
                            Integer openCount, Instant repliedAt, Instant bouncedAt, UUID campaignId,
                            String preview, Instant createdAt) {}
}
