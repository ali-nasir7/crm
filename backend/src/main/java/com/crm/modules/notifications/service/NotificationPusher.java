package com.crm.modules.notifications.service;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-process SSE registry. For multi-node deployments, back this with Redis pub/sub. */
@Component
public class NotificationPusher {

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(UUID userId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout; disconnects handled via callbacks
        emitters.put(userId, emitter);
        Runnable remove = () -> emitters.remove(userId);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(t -> remove.run());
        return emitter;
    }

    public void push(UUID userId, String event, Object payload) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name(event).data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(userId);
        }
    }
}
