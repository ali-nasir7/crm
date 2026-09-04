package com.crm.modules.chat.repo;

import com.crm.modules.chat.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query("select c from Conversation c where c.organizationId = :orgId and c.id = :id")
    Optional<Conversation> findInOrg(UUID orgId, UUID id);

    @Query(value = "select c.* from conversations c " +
        "join conversation_participants p on p.conversation_id = c.id " +
        "where p.user_id = :userId and c.organization_id = :orgId " +
        "order by coalesce(c.last_message_at, c.created_at) desc", nativeQuery = true)
    List<Conversation> findMine(@Param("orgId") UUID orgId, @Param("userId") UUID userId);

    /** Existing 1:1 conversation between two users, if any. */
    @Query(value = "select c.* from conversations c " +
        "join conversation_participants a on a.conversation_id = c.id and a.user_id = :me " +
        "join conversation_participants b on b.conversation_id = c.id and b.user_id = :other " +
        "where c.organization_id = :orgId and c.type = 'DIRECT' limit 1", nativeQuery = true)
    Optional<Conversation> findDirect(@Param("orgId") UUID orgId, @Param("me") UUID me, @Param("other") UUID other);
}
