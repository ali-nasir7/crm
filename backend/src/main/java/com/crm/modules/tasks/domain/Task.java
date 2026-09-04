package com.crm.modules.tasks.domain;

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
@Table(name = "tasks", indexes = {
    @Index(name = "ix_tasks_org_assignee_status", columnList = "organization_id, assigned_user_id, status"),
    @Index(name = "ix_tasks_org_due", columnList = "organization_id, due_at")})
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class Task extends TenantEntity {

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "contact_id")
    private UUID contactId;

    /** CALL, EMAIL, FOLLOW_UP, MEETING, PROPOSAL, OFFER, CHECK_RESPONSE, CUSTOM */
    @Column(name = "task_type", nullable = false, length = 24)
    private String taskType = "CUSTOM";

    @Column(name = "assigned_user_id", nullable = false)
    private UUID assignedUserId;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    /** LOW, MEDIUM, HIGH, URGENT */
    @Column(nullable = false, length = 8)
    private String priority = "MEDIUM";

    /** OPEN, COMPLETED, CANCELLED */
    @Column(nullable = false, length = 12)
    private String status = "OPEN";

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completion_note", length = 1000)
    private String completionNote;
}
