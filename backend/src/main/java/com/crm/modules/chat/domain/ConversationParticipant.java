package com.crm.modules.chat.domain;

import com.crm.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Membership of a user in a conversation, with the read marker. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "conversation_participants", uniqueConstraints =
    @UniqueConstraint(name = "uk_conv_participant", columnNames = {"conversation_id", "user_id"}),
    indexes = @Index(name = "ix_conv_participants_user", columnList = "user_id"))
public class ConversationParticipant extends BaseEntity {

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "last_read_at")
    private Instant lastReadAt;
}
