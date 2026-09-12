package com.crm.modules.campaigns.domain;

import com.crm.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-recipient progress through the sequence. The worker scans for rows with
 * status=IN_PROGRESS and next_send_at <= now() (index-backed).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "campaign_recipients", indexes = {
    @Index(name = "ix_crec_worker", columnList = "campaign_id, status, next_send_at"),
    @Index(name = "ix_crec_lead", columnList = "lead_id")})
public class CampaignRecipient extends BaseEntity {

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(nullable = false, length = 255)
    private String email;

    /** PENDING, IN_PROGRESS, COMPLETED, SKIPPED, UNSUBSCRIBED, BOUNCED, FAILED */
    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "current_step")
    private Integer currentStep;

    @Column(name = "next_send_at")
    private Instant nextSendAt;

    @Column(name = "last_email_id")
    private UUID lastEmailId;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;
}
