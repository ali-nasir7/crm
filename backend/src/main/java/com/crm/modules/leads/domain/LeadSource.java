package com.crm.modules.leads.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "lead_sources", uniqueConstraints = @UniqueConstraint(name = "uk_sources_org_key", columnNames = {"organization_id", "key"}),
       indexes = @Index(name = "ix_sources_org", columnList = "organization_id"))
@SQLRestriction("deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class LeadSource extends TenantEntity {

    @Column(nullable = false, length = 32)
    private String key;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 255)
    private String description;
}
