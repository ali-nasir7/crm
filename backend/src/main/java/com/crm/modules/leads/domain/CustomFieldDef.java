package com.crm.modules.leads.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.util.List;

/**
 * Per-organization custom lead fields (the clinic-niche fields are seeded as definitions).
 * Values live in leads.custom_fields (jsonb) keyed by {@link #key}; this definition drives
 * validation and the dynamic UI/filters.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "custom_field_defs", uniqueConstraints = @UniqueConstraint(name = "uk_cfd_org_key", columnNames = {"organization_id", "key"}),
       indexes = @Index(name = "ix_cfd_org", columnList = "organization_id"))
@SQLRestriction("deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class CustomFieldDef extends TenantEntity {

    @Column(nullable = false, length = 48)
    private String key;

    @Column(nullable = false, length = 80)
    private String label;

    /** TEXT, NUMBER, BOOLEAN, SELECT, MULTI_SELECT */
    @Column(nullable = false, length = 16)
    private String type;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "custom_field_options", joinColumns = @JoinColumn(name = "def_id"),
        indexes = @Index(name = "ix_cfo_def", columnList = "def_id"))
    @Column(name = "option_value", length = 64)
    private List<String> options;

    @Column(nullable = false)
    private int position;
}
