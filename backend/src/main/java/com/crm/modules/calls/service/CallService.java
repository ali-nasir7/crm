package com.crm.modules.calls.service;

import com.crm.common.api.PageResponse;
import com.crm.modules.activity.domain.ActivityType;
import com.crm.modules.activity.service.ActivityService;
import com.crm.modules.calls.domain.Call;
import com.crm.modules.calls.dto.CallDtos.*;
import com.crm.modules.calls.repo.CallRepository;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.leads.service.LeadAccessPolicy;
import com.crm.modules.tasks.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CallService {

    public static final Set<String> OUTCOMES = Set.of("NO_ANSWER", "BUSY", "WRONG_NUMBER", "CONNECTED",
        "INTERESTED", "NOT_INTERESTED", "CALL_BACK_LATER", "QUALIFIED", "MEETING_BOOKED");
    public static final Set<String> CONNECTED_OUTCOMES = Set.of("CONNECTED", "INTERESTED", "QUALIFIED", "MEETING_BOOKED");

    private final CallRepository calls;
    private final LeadAccessPolicy accessPolicy;
    private final ActivityService activities;
    private final UserRepository users;
    private final TaskService taskService;

    @Transactional
    public CallItem logCall(UUID orgId, UUID userId, UUID leadId, CreateCallRequest req) {
        var lead = accessPolicy.loadVisible(orgId, leadId);
        if (!OUTCOMES.contains(req.outcome())) {
            throw com.crm.common.api.ApiException.badRequest("Invalid call outcome: " + req.outcome());
        }
        Call call = new Call();
        call.setOrganizationId(orgId);
        call.setLeadId(leadId);
        call.setCompanyId(lead.getCompanyId());
        call.setUserId(userId);
        call.setDirection(req.direction() == null ? "OUTGOING" : req.direction());
        call.setOccurredAt(req.occurredAt() != null ? req.occurredAt() : Instant.now());
        call.setDurationSeconds(req.durationSeconds());
        call.setOutcome(req.outcome());
        call.setNotes(req.notes());
        call.setNextAction(req.nextAction());
        call.setFollowUpAt(req.followUpAt());
        calls.save(call);

        // timeline entry
        ActivityType type = "INCOMING".equals(call.getDirection()) ? ActivityType.INCOMING_CALL : ActivityType.OUTGOING_CALL;
        activities.record(orgId, type, leadId, "Call: " + req.outcome(), req.notes(),
            java.util.Map.of("outcome", req.outcome(), "durationSeconds", req.durationSeconds() == null ? 0 : req.durationSeconds()), userId);

        // lead is a managed entity in this transaction: contact state updates persist on commit
        lead.setLastContactedAt(call.getOccurredAt());
        if (req.followUpAt() != null) {
            lead.setNextFollowUpAt(req.followUpAt());
            taskService.createFollowUpFromCall(orgId, userId, lead, req.nextAction(), req.followUpAt());
        }
        return toItem(call);
    }

    @Transactional(readOnly = true)
    public PageResponse<CallItem> list(UUID orgId, UUID leadId, UUID userId, int page, int size) {
        var result = leadId != null
            ? calls.findByOrganizationIdAndLeadIdOrderByOccurredAtDesc(orgId, leadId, PageRequest.of(page, Math.min(size, 100)))
            : calls.findAll((root, cq, cb) -> {
                var org = cb.equal(root.get("organizationId"), orgId);
                if (userId != null) return cb.and(org, cb.equal(root.get("userId"), userId));
                return org;
            }, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "occurredAt")));
        return PageResponse.of(result.map(this::toItem));
    }

    public CallItem toItem(Call c) {
        return new CallItem(c.getId(), c.getLeadId(), null, c.getUserId(),
            users.findById(c.getUserId()).map(u -> u.displayName()).orElse(null),
            c.getDirection(), c.getOccurredAt(), c.getDurationSeconds(), c.getOutcome(), c.getNotes(),
            c.getNextAction(), c.getFollowUpAt(), c.getCreatedAt());
    }
}
