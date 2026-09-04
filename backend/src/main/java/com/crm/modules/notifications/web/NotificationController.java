package com.crm.modules.notifications.web;

import com.crm.common.api.PageResponse;
import com.crm.modules.notifications.service.NotificationPusher;
import com.crm.modules.notifications.service.NotificationService;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPusher pusher;

    @GetMapping
    public PageResponse<Map<String, Object>> list(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "25") int size) {
        return notificationService.list(CurrentUser.require().getId(), page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.unreadCount(CurrentUser.require().getId()));
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable UUID id) {
        notificationService.markRead(CurrentUser.require().getId(), id);
    }

    @PostMapping("/read-all")
    public Map<String, Object> markAllRead() {
        return Map.of("updated", notificationService.markAllRead(CurrentUser.require().getId()));
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return pusher.register(CurrentUser.require().getId());
    }
}
