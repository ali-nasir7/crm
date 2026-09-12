package com.crm.modules.chat.service;

import com.crm.common.api.ApiException;
import com.crm.common.api.PageResponse;
import com.crm.modules.chat.domain.Conversation;
import com.crm.modules.chat.domain.ConversationParticipant;
import com.crm.modules.chat.domain.ChatMessage;
import com.crm.modules.chat.repo.ChatMessageRepository;
import com.crm.modules.chat.repo.ConversationParticipantRepository;
import com.crm.modules.chat.repo.ConversationRepository;
import com.crm.modules.identity.domain.User;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Internal team chat. Authorization model (no bypass through chat):
 *  - rep <-> rep requires a SHARED TEAM;
 *  - rep <-> manager/admin is always allowed within the organization;
 *  - manager/admin can talk to anyone in their organization;
 *  - cross-organization access is impossible (org id comes from the JWT only).
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversations;
    private final ConversationParticipantRepository participants;
    private final ChatMessageRepository messages;
    private final UserRepository users;
    private final NotificationService notifications;

    public record ConversationItem(UUID id, List<UUID> participantIds, List<String> participantNames,
                                   String lastMessage, Instant lastMessageAt, long unreadCount) {}

    public record MessageItem(UUID id, UUID senderId, String senderName, String body,
                              UUID leadId, Instant createdAt) {}

    @Transactional(readOnly = true)
    public boolean canChatWith(UUID orgId, UUID meId, UUID otherId) {
        if (meId.equals(otherId)) return false;
        User me = users.findById(meId).orElseThrow();
        User other = users.findById(otherId)
            .filter(u -> u.getOrganizationId().equals(orgId) && u.getDeletedAt() == null)
            .orElseThrow(() -> ApiException.notFound("User not found in your organization"));
        if (me.isSuperAdmin() || other.isSuperAdmin()) return true;
        boolean meManager = hasManagerRole(me);
        boolean otherManager = hasManagerRole(other);
        if (meManager || otherManager) return true; // rep<->manager, manager<->anyone
        return me.getTeams().stream().map(t -> t.getId()).anyMatch(tid ->
            other.getTeams().stream().anyMatch(t2 -> t2.getId().equals(tid)));
    }

    @Transactional
    public ConversationItem openDirect(UUID orgId, UUID meId, UUID otherUserId) {
        if (!canChatWith(orgId, meId, otherUserId)) {
            throw ApiException.forbidden("You can only chat with teammates, or with managers/admins of your organization");
        }
        Conversation conv = conversations.findDirect(orgId, meId, otherUserId).orElse(null);
        if (conv == null) {
            conv = new Conversation();
            conv.setOrganizationId(orgId);
            conv.setType("DIRECT");
            conv = conversations.save(conv);
            Conversation finalConv = conv;
            for (UUID uid : new UUID[]{meId, otherUserId}) {
                ConversationParticipant p = new ConversationParticipant();
                p.setConversationId(finalConv.getId());
                p.setUserId(uid);
                participants.save(p);
            }
        }
        return toItem(orgId, meId, conv);
    }

    @Transactional(readOnly = true)
    public List<ConversationItem> listMine(UUID orgId, UUID userId) {
        return conversations.findMine(orgId, userId).stream()
            .map(c -> toItem(orgId, userId, c))
            .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageItem> messages(UUID orgId, UUID userId, UUID conversationId, int page, int size) {
        requireParticipant(orgId, userId, conversationId);
        Page<ChatMessage> result = messages.findByConversationIdOrderByCreatedAtDesc(
            conversationId, PageRequest.of(page, Math.min(size, 100)));
        var items = result.getContent().stream().map(this::toItem).toList();
        return PageResponse.of(items, result.getPageable(), result.getTotalElements());
    }

    @Transactional
    public MessageItem send(UUID orgId, UUID senderId, UUID conversationId, String body, UUID leadId) {
        requireParticipant(orgId, senderId, conversationId);
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) throw ApiException.badRequest("Message cannot be empty");
        if (text.length() > 4000) throw ApiException.badRequest("Message too long (max 4000 characters)");
        ChatMessage m = new ChatMessage();
        m.setOrganizationId(orgId);
        m.setConversationId(conversationId);
        m.setSenderId(senderId);
        m.setBody(text);
        m.setLeadId(leadId);
        messages.save(m);

        Conversation conv = conversations.findInOrg(orgId, conversationId).orElseThrow();
        conv.setLastMessageAt(m.getCreatedAt());
        conversations.save(conv);

        User sender = users.findById(senderId).orElseThrow();
        participants.findByConversationId(conversationId).stream()
            .filter(p -> !p.getUserId().equals(senderId))
            .forEach(p -> notifications.notify(orgId, p.getUserId(), "CHAT_MESSAGE",
                "New message from " + sender.displayName(),
                text.length() > 120 ? text.substring(0, 120) + "..." : text,
                "CONVERSATION", conversationId));
        return toItem(m);
    }

    @Transactional
    public void markRead(UUID orgId, UUID userId, UUID conversationId) {
        ConversationParticipant p = participants.findByConversationIdAndUserId(conversationId, userId)
            .filter(x -> conversations.findById(conversationId)
                .map(c -> c.getOrganizationId().equals(orgId)).orElse(false))
            .orElseThrow(() -> ApiException.notFound("Conversation not found"));
        p.setLastReadAt(Instant.now());
        participants.save(p);
    }

    @Transactional(readOnly = true)
    public long unreadTotal(UUID orgId, UUID userId) {
        return listMine(orgId, userId).stream().mapToLong(ConversationItem::unreadCount).sum();
    }

    // ---- helpers ----

    private void requireParticipant(UUID orgId, UUID userId, UUID conversationId) {
        conversations.findInOrg(orgId, conversationId)
            .orElseThrow(() -> ApiException.notFound("Conversation not found"));
        participants.findByConversationIdAndUserId(conversationId, userId)
            .orElseThrow(() -> ApiException.forbidden("You are not a participant of this conversation"));
    }

    private boolean hasManagerRole(User u) {
        return u.getRoles().stream().anyMatch(r ->
            "ADMIN".equals(r.getKey()) || "SALES_MANAGER".equals(r.getKey()));
    }

    private ConversationItem toItem(UUID orgId, UUID viewerId, Conversation c) {
        List<ConversationParticipant> ps = participants.findByConversationId(c.getId());
        List<UUID> ids = ps.stream().map(ConversationParticipant::getUserId).toList();
        List<String> names = ps.stream()
            .map(p -> users.findById(p.getUserId()).map(User::displayName).orElse("Unknown"))
            .toList();
        var last = messages.findByConversationIdOrderByCreatedAtDesc(c.getId(), PageRequest.of(0, 1))
            .getContent().stream().findFirst().orElse(null);
        Instant lastRead = ps.stream().filter(p -> p.getUserId().equals(viewerId)).findFirst()
            .map(ConversationParticipant::getLastReadAt).orElse(Instant.EPOCH);
        long unread = last == null ? 0
            : messages.countByConversationIdAndSenderIdNotAndCreatedAtAfter(c.getId(), viewerId, lastRead);
        return new ConversationItem(c.getId(), ids, names,
            last == null ? null : last.getBody(), c.getLastMessageAt() != null ? c.getLastMessageAt()
            : (last != null ? last.getCreatedAt() : null), unread);
    }

    private MessageItem toItem(ChatMessage m) {
        String name = users.findById(m.getSenderId()).map(User::displayName).orElse("Unknown");
        return new MessageItem(m.getId(), m.getSenderId(), name, m.getBody(), m.getLeadId(), m.getCreatedAt());
    }
}
