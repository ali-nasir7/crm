package com.crm.modules.deals.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "deals", indexes = {
    @Index(name = "ix_deals_org_stage", columnList = "organization_id, stage_id"),
    @Index(name = "ix_deals_org_owner", columnList = "organization_id, owner_id, status"),
    @Index(name = "ix_deals_org_created", columnList = "organization_id, created_at")})
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class Deal extends TenantEntity {

    @Column(nullable = false, length = 160)
    private String title;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "pipeline_id")
    private UUID pipelineId;

    @Column(name = "stage_id")
    private UUID stageId;

    @Column(name = "amount", precision = 16, scale = 2)
    private BigDecimal amount;

    @Column(length = 8)
    private String currency = "USD";

    @Column(nullable = false)
    private int probability;

    @Column(name = "expected_close_date")
    private Instant expectedCloseDate;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** OPEN, WON, LOST */
    @Column(nullable = false, length = 8)
    private String status = "OPEN";

    @Column(name = "lost_reason", length = 255)
    private String lostReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "products", columnDefinition = "jsonb")
    private List<String> products;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
