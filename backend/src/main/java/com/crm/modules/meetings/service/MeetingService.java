package com.crm.modules.meetings.service;

import com.crm.common.api.ApiException;
import com.crm.common.api.PageResponse;
import com.crm.modules.activity.domain.ActivityType;
import com.crm.modules.activity.service.ActivityService;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.meetings.domain.Meeting;
import com.crm.modules.meetings.dto.MeetingDtos.*;
import com.crm.modules.meetings.repo.MeetingRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private static final Set<String> STATUSES = Set.of("SCHEDULED", "COMPLETED", "CANCELLED", "NO_SHOW");

    private final MeetingRepository meetings;
    private final UserRepository users;
    private final ActivityService activities;

    @Transactional
    public MeetingItem create(UUID orgId, UUID actorId, CreateMeetingRequest req) {
        if (req.startAt() == null) throw ApiException.badRequest("Start time is required");
        Meeting m = new Meeting();
        m.setOrganizationId(orgId);
        m.setTitle(req.title().trim());
        m.setLeadId(req.leadId());
        m.setCompanyId(req.companyId());
        m.setOwnerId(req.leadId() == null ? actorId : actorId);
        m.setParticipants(req.participants());
        m.setStartAt(req.startAt());
        m.setDurationMinutes(req.durationMinutes() == null ? 30 : req.durationMinutes());
        m.setMeetingLink(req.meetingLink());
        m.setLocation(req.location());
        m.setNotes(req.notes());
        meetings.save(m);

        activities.record(orgId, ActivityType.MEETING, req.leadId(), "Meeting: " + m.getTitle(), req.notes(),
            Map.of("startAt", m.getStartAt().toString(), "durationMinutes", m.getDurationMinutes()), actorId);
        // TODO / Integration Required: Google Calendar / Microsoft Calendar sync hook
        return toItem(m);
    }

    @Transactional
    public MeetingItem update(UUID orgId, UUID id, UpdateMeetingRequest req) {
        Meeting m = find(orgId, id);
        if (req.title() != null) m.setTitle(req.title().trim());
        if (req.startAt() != null) m.setStartAt(req.startAt());
        if (req.durationMinutes() != null) m.setDurationMinutes(req.durationMinutes());
        if (req.meetingLink() != null) m.setMeetingLink(req.meetingLink());
        if (req.location() != null) m.setLocation(req.location());
        if (req.notes() != null) m.setNotes(req.notes());
        if (req.status() != null) {
            String s = req.status().toUpperCase();
            if (!STATUSES.contains(s)) throw ApiException.badRequest("Invalid status");
            m.setStatus(s);
        }
        meetings.save(m);
        return toItem(m);
    }

    @Transactional(readOnly = true)
    public PageResponse<MeetingItem> list(UUID orgId, UUID leadId, int page, int size) {
        Specification<Meeting> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("organizationId"), orgId));
            if (leadId != null) ps.add(cb.equal(root.get("leadId"), leadId));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<MeetingItem> result = meetings.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "startAt")))
            .map(this::toItem);
        return PageResponse.of(result.getContent(), result.getPageable(), result.getTotalElements());
    }

    @Transactional
    public void delete(UUID orgId, UUID id) {
        meetings.delete(find(orgId, id));
    }

    private Meeting find(UUID orgId, UUID id) {
        return meetings.findById(id).filter(m -> m.getOrganizationId().equals(orgId))
            .orElseThrow(() -> ApiException.notFound("Meeting not found"));
    }

    public MeetingItem toItem(Meeting m) {
        return new MeetingItem(m.getId(), m.getTitle(), m.getLeadId(), null, m.getCompanyId(), m.getOwnerId(),
            users.findById(m.getOwnerId()).map(u -> u.displayName()).orElse(null), m.getParticipants(),
            m.getStartAt(), m.getDurationMinutes(), m.getMeetingLink(), m.getLocation(), m.getNotes(),
            m.getStatus(), m.getCreatedAt());
    }
}
