package com.crm.modules.chat.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** One chat message. Optionally references a lead so reps can share CRM context. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "chat_messages", indexes = @Index(name = "ix_chat_messages_conv", columnList = "conversation_id, created_at"))
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class ChatMessage extends TenantEntity {

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "lead_id")
    private UUID leadId;
}
