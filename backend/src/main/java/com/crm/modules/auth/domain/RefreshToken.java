package com.crm.modules.auth.domain;

import com.crm.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Opaque refresh token; only the SHA-256 hash is stored. Rotated on every use. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "refresh_tokens",
    uniqueConstraints = @UniqueConstraint(name = "uk_rt_hash", columnNames = "token_hash"),
    indexes = @Index(name = "ix_rt_user", columnList = "user_id"))
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    public boolean isActive() { return revokedAt == null && expiresAt.isAfter(Instant.now()); }
}
