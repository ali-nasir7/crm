package com.crm.modules.notifications.domain;

import com.crm.common.domain.BaseEntity;
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
@Table(name = "notifications", indexes = {
    @Index(name = "ix_notif_user", columnList = "user_id, created_at"),
    @Index(name = "ix_notif_org", columnList = "organization_id")})
public class Notification extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "entity_type", length = 32)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "read_at")
    private Instant readAt;

    public boolean isUnread() { return readAt == null; }
}
