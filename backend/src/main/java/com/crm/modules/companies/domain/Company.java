package com.crm.modules.companies.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "companies", indexes = {
    @Index(name = "ix_companies_org_name", columnList = "organization_id, deleted_at, name"),
    @Index(name = "ix_companies_org_created", columnList = "organization_id, deleted_at, created_at")})
@SQLRestriction("deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class Company extends TenantEntity {

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 255)
    private String website;

    @Column(length = 80)
    private String industry;

    @Column(length = 2000)
    private String description;

    @Column(length = 32)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(length = 64)
    private String country;

    @Column(length = 64)
    private String city;

    @Column(length = 64)
    private String state;

    @Column(length = 255)
    private String address;

    @Column(length = 255)
    private String linkedin;

    /** e.g. 1-10, 11-50, 51-200, 201-500, 501-1000, 1000+ */
    @Column(name = "company_size", length = 32)
    private String companySize;

    @Column(name = "annual_revenue", length = 32)
    private String annualRevenue;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "company_tags",
        joinColumns = @JoinColumn(name = "company_id"), inverseJoinColumns = @JoinColumn(name = "tag_id"),
        indexes = {@Index(name = "ix_ct_company", columnList = "company_id"), @Index(name = "ix_ct_tag", columnList = "tag_id")})
    private Set<com.crm.modules.leads.domain.Tag> tags = new LinkedHashSet<>();
}
