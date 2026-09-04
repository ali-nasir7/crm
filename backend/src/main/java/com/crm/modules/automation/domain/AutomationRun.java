package com.crm.modules.automation.domain;

import com.crm.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Execution log for auditability of every automated action. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "automation_runs", indexes = {
    @Index(name = "ix_autoruns_rule", columnList = "rule_id"),
    @Index(name = "ix_autoruns_lead", columnList = "lead_id")})
public class AutomationRun extends BaseEntity {

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "lead_id")
    private UUID leadId;

    /** EXECUTED, SKIPPED, FAILED */
    @Column(nullable = false, length = 12)
    private String status;

    @Column(name = "detail", length = 1000)
    private String detail;
}
