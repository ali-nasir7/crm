package com.crm.modules.chat.web;

import com.crm.common.api.PageResponse;
import com.crm.modules.chat.service.ChatService;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Internal team chat. Any authenticated user; authorization enforced in ChatService. */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat")
public class ChatController {

    private final ChatService chat;

    public record OpenConversationRequest(UUID userId) {}
    public record SendMessageRequest(String body, UUID leadId) {}

    @GetMapping("/conversations")
    public List<ChatService.ConversationItem> myConversations() {
        return chat.listMine(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId());
    }

    @PostMapping("/conversations")
    public ChatService.ConversationItem openDirect(@RequestBody OpenConversationRequest request) {
        return chat.openDirect(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), request.userId());
    }

    @GetMapping("/conversations/{id}/messages")
    public PageResponse<ChatService.MessageItem> messages(@PathVariable UUID id,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "30") int size) {
        return chat.messages(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), id, page, size);
    }

    @PostMapping("/conversations/{id}/messages")
    public ChatService.MessageItem send(@PathVariable UUID id, @RequestBody SendMessageRequest request) {
        return chat.send(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(),
            id, request.body(), request.leadId());
    }

    @PostMapping("/conversations/{id}/read")
    public Map<String, String> markRead(@PathVariable UUID id) {
        chat.markRead(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), id);
        return Map.of("ok", "true");
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", chat.unreadTotal(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId()));
    }
}
