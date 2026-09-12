package com.crm.modules.chat.repo;

import com.crm.modules.chat.domain.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    Page<ChatMessage> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    List<ChatMessage> findByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID conversationId, Instant after, Pageable pageable);

    long countByConversationIdAndSenderIdNotAndCreatedAtAfter(UUID conversationId, UUID senderId, Instant after);
}
