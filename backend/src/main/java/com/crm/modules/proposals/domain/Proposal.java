package com.crm.modules.proposals.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "proposals", indexes = {
    @Index(name = "ix_proposals_org_created", columnList = "organization_id, created_at"),
    @Index(name = "ix_proposals_lead", columnList = "lead_id")})
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class Proposal extends TenantEntity {

    @Column(name = "proposal_number", nullable = false, length = 24)
    private String proposalNumber;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "deal_id")
    private UUID dealId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    /** DRAFT, SENT, VIEWED, ACCEPTED, REJECTED, EXPIRED */
    @Column(nullable = false, length = 12)
    private String status = "DRAFT";

    @Column(length = 8)
    private String currency = "USD";

    @Column(name = "discount_percent", precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "tax_percent", precision = 5, scale = 2)
    private BigDecimal taxPercent;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(columnDefinition = "text")
    private String terms;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "viewed_at")
    private Instant viewedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

}
