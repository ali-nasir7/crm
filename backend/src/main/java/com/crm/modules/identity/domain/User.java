package com.crm.modules.identity.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "users",
    uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
    indexes = @Index(name = "ix_users_org", columnList = "organization_id"))
@SQLRestriction("deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class User extends TenantEntity {

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "job_title", length = 80)
    private String jobTitle;

    @Column(length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "super_admin", nullable = false)
    private boolean superAdmin;

    @Column(name = "timezone", length = 64)
    private String timezone = "UTC";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "daily_targets", columnDefinition = "jsonb")
    private Map<String, Integer> dailyTargets; // calls, emails, meetings

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** True while the account still uses an admin-issued temporary password. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "members")
    private Set<Team> teams = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"),
        indexes = {@Index(name = "ix_ur_user", columnList = "user_id"), @Index(name = "ix_ur_role", columnList = "role_id")})
    private Set<Role> roles = new HashSet<>();

    public String displayName() { return firstName + " " + lastName; }
}
