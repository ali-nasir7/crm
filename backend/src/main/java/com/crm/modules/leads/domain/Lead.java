package com.crm.modules.leads.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/** The central object of the CRM: a complete lead profile with niche fields in custom_fields. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "leads", indexes = {
    @Index(name = "ix_leads_org_status", columnList = "organization_id, deleted_at, status"),
    @Index(name = "ix_leads_org_assignee", columnList = "organization_id, deleted_at, assigned_user_id"),
    @Index(name = "ix_leads_org_stage", columnList = "organization_id, deleted_at, stage_id"),
    @Index(name = "ix_leads_org_created", columnList = "organization_id, deleted_at, created_at"),
    @Index(name = "ix_leads_org_followup", columnList = "organization_id, deleted_at, next_followup_at"),
    @Index(name = "ix_leads_org_contacted", columnList = "organization_id, deleted_at, last_contacted_at"),
    @Index(name = "ix_leads_org_score", columnList = "organization_id, deleted_at, score")})
@SQLRestriction("deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class Lead extends TenantEntity {

    // --- identification ---
    @Column(name = "business_name", nullable = false, length = 160)
    private String businessName;

    @Column(name = "first_name", length = 80)
    private String firstName;

    @Column(name = "last_name", length = 80)
    private String lastName;

    @Column(name = "job_title", length = 80)
    private String jobTitle;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "contact_id")
    private UUID contactId;

    // --- contact channels ---
    @Column(length = 255)
    private String email;

    @Column(name = "secondary_email", length = 255)
    private String secondaryEmail;

    @Column(length = 32)
    private String phone;

    @Column(length = 32)
    private String whatsapp;

    @Column(length = 255)
    private String website;

    @Column(length = 255)
    private String linkedin;

    // --- location ---
    @Column(length = 64)
    private String country;

    @Column(length = 64)
    private String state;

    @Column(length = 64)
    private String city;

    @Column(length = 255)
    private String address;

    @Column(length = 64)
    private String timezone;

    // --- business profile ---
    @Column(length = 80)
    private String industry;

    @Column(name = "business_type", length = 64)
    private String businessType;

    @Column(name = "company_size", length = 32)
    private String companySize;

    @Column(name = "employees_count")
    private Integer employeesCount;

    @Column(name = "revenue_range", length = 32)
    private String revenueRange;

    // --- niche fields (clinic vertical seeded; fully configurable per org) ---
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "jsonb")
    private Map<String, Object> customFields = new LinkedHashMap<>();

    // --- classification ---
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LeadStatus status = LeadStatus.NEW;

    @Column(nullable = false)
    private int score;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "pipeline_id")
    private UUID pipelineId;

    @Column(name = "stage_id")
    private UUID stageId;

    // --- ownership & lifecycle ---
    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "last_contacted_at")
    private Instant lastContactedAt;

    @Column(name = "next_followup_at")
    private Instant nextFollowUpAt;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // --- relationships ---
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "lead_tags",
        joinColumns = @JoinColumn(name = "lead_id"), inverseJoinColumns = @JoinColumn(name = "tag_id"),
        indexes = {@Index(name = "ix_lt_lead", columnList = "lead_id"), @Index(name = "ix_lt_tag", columnList = "tag_id")})
    private Set<Tag> tags = new LinkedHashSet<>();

    public String contactDisplayName() {
        if (firstName == null && lastName == null) return null;
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }
}
