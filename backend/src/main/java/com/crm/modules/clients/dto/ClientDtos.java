package com.crm.modules.clients.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ClientDtos {
    private ClientDtos() {}

    public record UpdateClientRequest(UUID accountManagerId, String status, @Size(max = 5000) String notes) {}

    public record ClientItem(UUID id, UUID companyId, String companyName, String website, UUID primaryContactId,
                             String primaryContactName, UUID accountManagerId, String accountManagerName,
                             String status, BigDecimal lifetimeValue, UUID convertedFromLeadId, Instant convertedAt,
                             String notes, Instant createdAt) {}
}
