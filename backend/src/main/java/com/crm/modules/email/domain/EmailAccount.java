package com.crm.modules.email.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-user sending identity. SMTP credentials are stored AES-256-GCM encrypted.
 * GMAIL / M365 entries carry OAuth token placeholders — TODO / Integration Required (needs
 * provider OAuth apps per deployment; see SECURITY.md).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "email_accounts", indexes = @Index(name = "ix_eaccounts_org_user", columnList = "organization_id, user_id"))
@SQLRestriction("deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class EmailAccount extends TenantEntity {

    public enum Provider { SMTP, GMAIL, M365 }

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Provider provider = Provider.SMTP;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "smtp_host", length = 255)
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    /** STARTTLS, SSL, NONE */
    @Column(name = "smtp_encryption", length = 12)
    private String smtpEncryption = "STARTTLS";

    @Column(name = "smtp_username", length = 255)
    private String smtpUsername;

    @Column(name = "smtp_password_enc", length = 1000)
    private String smtpPasswordEnc;

    /** PENDING, VERIFIED, FAILED */
    @Column(nullable = false, length = 12)
    private String status = "PENDING";

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "daily_limit", nullable = false)
    private int dailyLimit = 200;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
