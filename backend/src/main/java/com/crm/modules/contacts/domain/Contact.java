package com.crm.modules.contacts.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "contacts", indexes = {
    @Index(name = "ix_contacts_org_company", columnList = "organization_id, company_id"),
    @Index(name = "ix_contacts_org_created", columnList = "organization_id, deleted_at, created_at")})
@SQLRestriction("deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class Contact extends TenantEntity {

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "job_title", length = 80)
    private String jobTitle;

    @Column(length = 255)
    private String email;

    @Column(name = "secondary_email", length = 255)
    private String secondaryEmail;

    @Column(length = 32)
    private String phone;

    @Column(length = 32)
    private String whatsapp;

    @Column(length = 255)
    private String linkedin;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public String displayName() { return firstName + " " + lastName; }
}
