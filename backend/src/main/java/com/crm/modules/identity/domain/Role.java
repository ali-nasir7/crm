package com.crm.modules.identity.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "roles", uniqueConstraints = @UniqueConstraint(name = "uk_roles_org_key", columnNames = {"organization_id", "key"}),
       indexes = @Index(name = "ix_roles_org", columnList = "organization_id"))
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class Role extends TenantEntity {

    @Column(name = "key", nullable = false, length = 48)
    private String key;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_scope", nullable = false, length = 8)
    private DataScope dataScope = DataScope.ORG;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"), inverseJoinColumns = @JoinColumn(name = "permission_id"),
        indexes = {@Index(name = "ix_rp_role", columnList = "role_id"), @Index(name = "ix_rp_perm", columnList = "permission_id")})
    private Set<Permission> permissions = new HashSet<>();
}
