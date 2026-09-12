package com.crm.modules.contacts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ContactDtos {
    private ContactDtos() {}

    public record CreateContactRequest(
        UUID companyId,
        @NotBlank @Size(max = 80) String firstName, @NotBlank @Size(max = 80) String lastName,
        @Size(max = 80) String jobTitle, @jakarta.validation.constraints.Email @Size(max = 255) String email,
        @Size(max = 255) String secondaryEmail, @Size(max = 32) String phone, @Size(max = 32) String whatsapp,
        @Size(max = 255) String linkedin, UUID ownerId, boolean primary, @Size(max = 2000) String notes) {}

    public record UpdateContactRequest(
        UUID companyId,
        @NotBlank @Size(max = 80) String firstName, @NotBlank @Size(max = 80) String lastName,
        @Size(max = 80) String jobTitle, @jakarta.validation.constraints.Email @Size(max = 255) String email,
        @Size(max = 255) String secondaryEmail, @Size(max = 32) String phone, @Size(max = 32) String whatsapp,
        @Size(max = 255) String linkedin, UUID ownerId, Boolean primary, @Size(max = 2000) String notes) {}

    public record ContactItem(UUID id, UUID companyId, String companyName, String firstName, String lastName,
                              String displayName, String jobTitle, String email, String secondaryEmail,
                              String phone, String whatsapp, String linkedin, UUID ownerId, String ownerName,
                              boolean primary, String notes, Instant createdAt, Instant updatedAt) {}
}
