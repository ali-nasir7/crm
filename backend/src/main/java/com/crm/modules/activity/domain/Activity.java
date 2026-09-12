package com.crm.modules.activity.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Unified timeline entry. Every meaningful event (manual or system-generated) lands here so a lead
 * page can render one chronological feed. Structured records (calls, emails, tasks...) also live in
 * their own tables for reporting; activities mirror them.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "activities", indexes = {
    @Index(name = "ix_act_org_lead", columnList = "organization_id, lead_id, occurred_at"),
    @Index(name = "ix_act_org_created", columnList = "organization_id, created_at"),
    @Index(name = "ix_act_org_type", columnList = "organization_id, type")})
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class Activity extends TenantEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ActivityType type;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "deal_id")
    private UUID dealId;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(length = 160)
    private String subject;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
