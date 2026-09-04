package com.crm.modules.deals.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DealDtos {
    private DealDtos() {}

    public record CreateDealRequest(@NotBlank @Size(max = 160) String title, UUID leadId, UUID companyId, UUID contactId,
                                    UUID ownerId, UUID pipelineId, UUID stageId, BigDecimal amount, String currency,
                                    Integer probability, Instant expectedCloseDate, List<String> products,
                                    @Size(max = 5000) String notes) {}

    public record UpdateDealRequest(@Size(max = 160) String title, BigDecimal amount, String currency,
                                    Integer probability, Instant expectedCloseDate, List<String> products,
                                    @Size(max = 5000) String notes) {}

    public record StageRequest(UUID stageId) {}
    public record StatusRequest(String status, String lostReason) {}

    public record DealItem(UUID id, String title, UUID leadId, String businessName, UUID companyId, String companyName,
                           UUID contactId, UUID ownerId, String ownerName, UUID pipelineId, UUID stageId, String stageName,
                           BigDecimal amount, String currency, int probability, Instant expectedCloseDate,
                           Instant closedAt, String status, String lostReason, List<String> products, String notes,
                           UUID clientId, Instant createdAt) {}

    public record DealSummary(BigDecimal openValue, BigDecimal weightedValue, BigDecimal wonRevenue, BigDecimal lostRevenue,
                              long openCount, long wonCount, long lostCount, BigDecimal expectedRevenue) {}
}
