package com.crm.modules.calls.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "calls", indexes = {
    @Index(name = "ix_calls_org_created", columnList = "organization_id, created_at"),
    @Index(name = "ix_calls_org_user", columnList = "organization_id, user_id, created_at"),
    @Index(name = "ix_calls_lead", columnList = "lead_id")})
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class Call extends TenantEntity {

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "device_id")
    private UUID deviceId;

    /** INITIATING / RINGING / CONNECTED / ENDED / NO_ANSWER / BUSY / FAILED (live-call tracking). */
    @Column(nullable = false, length = 16)
    private String status = "ENDED";

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "provider_ref", length = 64)
    private String providerRef;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 12)
    private String direction; // OUTGOING, INCOMING

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /** NO_ANSWER, BUSY, WRONG_NUMBER, CONNECTED, INTERESTED, NOT_INTERESTED, CALL_BACK_LATER, QUALIFIED, MEETING_BOOKED */
    @Column(nullable = false, length = 24)
    private String outcome;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "next_action", length = 255)
    private String nextAction;

    @Column(name = "follow_up_at")
    private Instant followUpAt;
}
