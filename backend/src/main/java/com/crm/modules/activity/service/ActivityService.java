package com.crm.modules.activity.service;

import com.crm.common.api.PageResponse;
import com.crm.modules.activity.domain.Activity;
import com.crm.modules.activity.domain.ActivityType;
import com.crm.modules.activity.dto.ActivityDtos.ActivityItem;
import com.crm.modules.activity.repo.ActivityRepository;
import com.crm.modules.identity.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activities;
    private final UserRepository users;

    /** System-generated timeline entry (stage changes, assignments, conversions, imports, ...). */
    @Transactional
    public void record(UUID orgId, ActivityType type, UUID leadId, String subject, String body,
                       Map<String, Object> metadata, UUID actorId) {
        Activity a = new Activity();
        a.setOrganizationId(orgId);
        a.setType(type);
        a.setLeadId(leadId);
        a.setActorId(actorId);
        a.setSubject(subject);
        a.setBody(body);
        a.setMetadata(metadata);
        a.setOccurredAt(Instant.now());
        activities.save(a);
    }

    @Transactional
    public ActivityItem addNote(UUID orgId, UUID userId, UUID leadId, String body) {
        Activity a = new Activity();
        a.setOrganizationId(orgId);
        a.setType(ActivityType.NOTE);
        a.setLeadId(leadId);
        a.setActorId(userId);
        a.setSubject("Note");
        a.setBody(body);
        a.setOccurredAt(Instant.now());
        activities.save(a);
        return toItem(a, actorName(userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityItem> timeline(UUID orgId, UUID leadId, int page, int size) {
        var result = activities.findByOrganizationIdAndLeadIdOrderByOccurredAtDesc(orgId, leadId, PageRequest.of(page, Math.min(size, 100)));
        return PageResponse.of(result.map(a -> toItem(a, actorName(a.getActorId()))));
    }

    public ActivityItem toItem(Activity a, String actorName) {
        return new ActivityItem(a.getId(), a.getType().name(), a.getLeadId(), a.getActorId(), actorName,
            a.getSubject(), a.getBody(), a.getMetadata(), a.getOccurredAt(), a.getCreatedAt());
    }

    private String actorName(UUID actorId) {
        return actorId == null ? null : users.findById(actorId).map(u -> u.displayName()).orElse(null);
    }
}
