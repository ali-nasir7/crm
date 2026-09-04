package com.crm.modules.proposals.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProposalDtos {
    private ProposalDtos() {}

    public record ItemRequest(@NotBlank @Size(max = 160) String name, @Size(max = 2000) String description,
                              BigDecimal quantity, BigDecimal unitPrice, Integer position) {}

    public record CreateProposalRequest(@NotBlank @Size(max = 160) String title, @Size(max = 10000) String description,
                                        UUID leadId, UUID dealId, UUID companyId, UUID contactId, String currency,
                                        BigDecimal discountPercent, BigDecimal taxPercent, Instant validUntil,
                                        String terms, List<ItemRequest> items) {}

    public record UpdateProposalRequest(@Size(max = 160) String title, @Size(max = 10000) String description,
                                        String currency, BigDecimal discountPercent, BigDecimal taxPercent,
                                        Instant validUntil, String terms) {}

    public record StatusRequest(String status) {}
    public record SendRequest(String toEmail, String message) {}

    public record ItemItem(UUID id, String name, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal total) {}

    public record ProposalItem_(UUID id, String proposalNumber, String title, String description, String status,
                                UUID leadId, String businessName, UUID dealId, UUID companyId, String companyName,
                                UUID contactId, String contactName, String currency, BigDecimal subtotal,
                                BigDecimal discountPercent, BigDecimal discountAmount, BigDecimal taxPercent,
                                BigDecimal taxAmount, BigDecimal total, Instant validUntil, String terms,
                                Instant sentAt, Instant viewedAt, Instant decidedAt,
                                List<ItemItem> items, Instant createdAt) {}
}
