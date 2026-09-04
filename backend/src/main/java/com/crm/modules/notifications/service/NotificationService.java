package com.crm.modules.notifications.service;

import com.crm.common.api.PageResponse;
import com.crm.modules.notifications.domain.Notification;
import com.crm.modules.notifications.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final NotificationPusher pusher;

    @Transactional
    public void notify(UUID orgId, UUID userId, String type, String title, String body, String entityType, UUID entityId) {
        Notification n = new Notification();
        n.setOrganizationId(orgId);
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setEntityType(entityType);
        n.setEntityId(entityId);
        notifications.save(n);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", n.getId());
        payload.put("type", type);
        payload.put("title", title);
        payload.put("body", body);
        payload.put("entityType", entityType);
        payload.put("entityId", entityId);
        payload.put("createdAt", n.getCreatedAt());
        pusher.push(userId, "notification", payload);
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(UUID userId, int page, int size) {
        var result = notifications.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, Math.min(size, 100)));
        var content = result.map(n -> {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", n.getId());
            m.put("type", n.getType());
            m.put("title", n.getTitle());
            m.put("body", n.getBody());
            m.put("entityType", n.getEntityType());
            m.put("entityId", n.getEntityId());
            m.put("readAt", n.getReadAt());
            m.put("createdAt", n.getCreatedAt());
            return m;
        });
        return PageResponse.of(content);
    }

    public long unreadCount(UUID userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID id) {
        notifications.findById(id).filter(n -> n.getUserId().equals(userId)).ifPresent(n -> n.setReadAt(Instant.now()));
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notifications.markAllRead(userId, Instant.now());
    }
}
