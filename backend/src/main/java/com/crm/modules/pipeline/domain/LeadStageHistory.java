package com.crm.modules.pipeline.domain;

import com.crm.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Immutable record of every stage transition, with time-in-stage for bottleneck analytics. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "lead_stage_history", indexes = {
    @Index(name = "ix_lsh_lead", columnList = "lead_id, entered_at"),
    @Index(name = "ix_lsh_stage", columnList = "to_stage_id")})
public class LeadStageHistory extends BaseEntity {

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "from_stage_id")
    private UUID fromStageId;

    @Column(name = "to_stage_id", nullable = false)
    private UUID toStageId;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;
}
