package com.crm.modules.companies.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CompanyDtos {
    private CompanyDtos() {}

    public record CreateCompanyRequest(
        @NotBlank @Size(max = 160) String name, @Size(max = 255) String website, @Size(max = 80) String industry,
        @Size(max = 2000) String description, @Size(max = 32) String phone, @Size(max = 255) String email,
        @Size(max = 64) String country, @Size(max = 64) String city, @Size(max = 64) String state,
        @Size(max = 255) String address, @Size(max = 255) String linkedin,
        @Size(max = 32) String companySize, @Size(max = 32) String annualRevenue,
        UUID ownerId, List<String> tags) {}

    public record UpdateCompanyRequest(
        @Size(max = 160) String name, @Size(max = 255) String website, @Size(max = 80) String industry,
        @Size(max = 2000) String description, @Size(max = 32) String phone, @Size(max = 255) String email,
        @Size(max = 64) String country, @Size(max = 64) String city, @Size(max = 64) String state,
        @Size(max = 255) String address, @Size(max = 255) String linkedin,
        @Size(max = 32) String companySize, @Size(max = 32) String annualRevenue,
        UUID ownerId, List<String> tags) {}

    public record CompanyItem(UUID id, String name, String website, String industry, String description,
                              String phone, String email, String country, String city, String state, String address,
                              String linkedin, String companySize, String annualRevenue,
                              UUID ownerId, String ownerName, List<String> tags,
                              Instant createdAt, Instant updatedAt) {}
}
