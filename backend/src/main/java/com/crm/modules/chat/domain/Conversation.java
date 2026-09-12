package com.crm.modules.chat.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A chat thread. V1 supports DIRECT (1:1) conversations within one organization. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "conversations", indexes = @Index(name = "ix_conversations_org", columnList = "organization_id"))
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class Conversation extends TenantEntity {

    /** DIRECT only in V1; the column exists so GROUP can be added without a migration. */
    @Column(nullable = false, length = 16)
    private String type = "DIRECT";

    @Column(name = "last_message_at")
    private Instant lastMessageAt;
}
