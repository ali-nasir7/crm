package com.crm.modules.leads.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "tags", uniqueConstraints = @UniqueConstraint(name = "uk_tags_org_name", columnNames = {"organization_id", "name"}),
       indexes = @Index(name = "ix_tags_org", columnList = "organization_id"))
@SQLRestriction("deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class Tag extends TenantEntity {

    @Column(nullable = false, length = 48)
    private String name;

    @Column(length = 9)
    private String color;

    /** Soft-delete marker; @SQLRestriction hides soft-deleted rows from all ORM queries. */
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
