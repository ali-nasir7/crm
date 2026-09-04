package com.crm.modules.campaigns.domain;

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
@Table(name = "campaigns", indexes = @Index(name = "ix_campaigns_org", columnList = "organization_id, status"))
@SQLRestriction("deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class Campaign extends TenantEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "account_id")
    private UUID accountId;

    /** DRAFT, SCHEDULED, RUNNING, PAUSED, COMPLETED, CANCELLED */
    @Column(nullable = false, length = 12)
    private String status = "DRAFT";

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    // denormalized counters (fast stats; source of truth remains emails/recipients)
    @Column(name = "total_recipients", nullable = false)
    private int totalRecipients;

    @Column(name = "sent_count", nullable = false)
    private int sentCount;

    @Column(name = "open_count", nullable = false)
    private int openCount;

    @Column(name = "reply_count", nullable = false)
    private int replyCount;

    @Column(name = "bounce_count", nullable = false)
    private int bounceCount;

    @Column(name = "unsubscribe_count", nullable = false)
    private int unsubscribeCount;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
