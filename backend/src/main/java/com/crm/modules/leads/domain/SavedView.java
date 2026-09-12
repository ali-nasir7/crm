package com.crm.modules.leads.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/** A named, shareable set of lead list filters ("My Hot Leads", "Dubai Clinics", ...). */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "saved_views", indexes = @Index(name = "ix_views_org_owner", columnList = "organization_id, owner_id"))
@SQLRestriction("deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class SavedView extends TenantEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "owner_id", nullable = false)
    private java.util.UUID ownerId;

    @Column(name = "is_shared", nullable = false)
    private boolean shared;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> filters;

    @Column(length = 64)
    private String sort;
}
