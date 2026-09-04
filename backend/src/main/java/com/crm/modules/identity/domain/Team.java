package com.crm.modules.identity.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.util.HashSet;
import java.util.UUID;
import java.time.Instant;
import java.util.Set;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "teams", uniqueConstraints = @UniqueConstraint(name = "uk_teams_org_name", columnNames = {"organization_id", "name"}),
       indexes = @Index(name = "ix_teams_org", columnList = "organization_id"))
@SQLRestriction("deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class Team extends TenantEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "manager_id")
    private UUID managerId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "team_members",
        joinColumns = @JoinColumn(name = "team_id"), inverseJoinColumns = @JoinColumn(name = "user_id"),
        indexes = {@Index(name = "ix_tm_team", columnList = "team_id"), @Index(name = "ix_tm_user", columnList = "user_id")})
    private Set<User> members = new HashSet<>();
}
