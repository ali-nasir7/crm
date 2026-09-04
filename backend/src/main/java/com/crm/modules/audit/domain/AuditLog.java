package com.crm.modules.audit.domain;

import com.crm.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

/** Append-only audit trail. No update/delete code paths exist. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "audit_logs", indexes = {
    @Index(name = "ix_audit_org_created", columnList = "organization_id, created_at"),
    @Index(name = "ix_audit_entity", columnList = "organization_id, entity_type, entity_id"),
    @Index(name = "ix_audit_actor", columnList = "actor_id")})
public class AuditLog extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Column(name = "action", nullable = false, length = 48)
    private String action;

    @Column(name = "entity_type", length = 32)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "entity_label", length = 255)
    private String entityLabel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb")
    private Map<String, Object> oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb")
    private Map<String, Object> newValues;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;
}
