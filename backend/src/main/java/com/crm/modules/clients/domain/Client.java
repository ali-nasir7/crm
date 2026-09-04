package com.crm.modules.clients.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Converted customer account. Linked to its company/contact and preserving the origin lead. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "clients", indexes = {
    @Index(name = "ix_clients_org", columnList = "organization_id, deleted_at, status"),
    @Index(name = "ix_clients_company", columnList = "company_id")})
@SQLRestriction("deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class Client extends TenantEntity {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "primary_contact_id")
    private UUID primaryContactId;

    @Column(name = "account_manager_id")
    private UUID accountManagerId;

    /** ACTIVE, ONBOARDING, AT_RISK, INACTIVE, CHURNED */
    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "lifetime_value", precision = 16, scale = 2)
    private BigDecimal lifetimeValue;

    @Column(name = "converted_from_lead_id")
    private UUID convertedFromLeadId;

    @Column(name = "converted_at", nullable = false)
    private Instant convertedAt;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
