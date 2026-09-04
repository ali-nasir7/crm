package com.crm.modules.leads.service;

import com.crm.common.api.ApiException;
import com.crm.common.api.PageResponse;
import com.crm.modules.activity.domain.ActivityType;
import com.crm.modules.activity.service.ActivityService;
import com.crm.modules.audit.service.AuditService;
import com.crm.modules.automation.service.AutomationEngine;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.leads.domain.*;
import com.crm.modules.leads.dto.LeadDtos.*;
import com.crm.modules.leads.repo.LeadRepository;
import com.crm.modules.leads.repo.TagRepository;
import com.crm.modules.notifications.service.NotificationService;
import com.crm.modules.pipeline.repo.PipelineRepository;
import com.crm.modules.pipeline.repo.PipelineStageRepository;
import com.crm.modules.pipeline.service.PipelineService;
import com.crm.security.CurrentUser;
import com.crm.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leads;
    private final com.crm.modules.leads.repo.LeadSourceRepository sources;
    private final TagRepository tags;
    private final UserRepository users;
    private final LeadAccessPolicy accessPolicy;
    private final LeadScoringService scoring;
    private final DuplicateDetectionService duplicates;
    private final ActivityService activities;
    private final AuditService audit;
    private final NotificationService notifications;
    private final PipelineService pipelineService;
    private final PipelineRepository pipelines;
    private final PipelineStageRepository stages;
    private final AutomationEngine automationEngine;

    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "score", "businessName", "lastContactedAt", "nextFollowUpAt", "status");

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public PageResponse<LeadItem> list(UserPrincipal principal, UUID orgId, LeadSpecs.LeadFilters filters,
                                       int page, int size, String sort) {
        Sort springSort = parseSort(sort);
        var spec = accessPolicy.visibility(orgId, principal).and(LeadSpecs.from(filters));
        Page<Lead> result = leads.findAll(spec, PageRequest.of(page, Math.min(size, 200), springSort));
        return PageResponse.of(result.map(l -> toItem(l, orgId)));
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) return Sort.by(Sort.Direction.DESC, "createdAt");
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!SORTABLE.contains(field)) field = "createdAt";
        Sort.Direction dir = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, field);
    }

    @Transactional(readOnly = true)
    public LeadItem get(UUID orgId, UUID leadId) {
        return toItem(accessPolicy.loadVisible(orgId, leadId), orgId);
    }

    // ---------- commands ----------

    @Transactional
    public LeadItem create(UUID orgId, CreateLeadRequest req) {
        if (req.email() != null && !req.email().isBlank()) {
            duplicates.assertNoDuplicate(orgId, req.email(), req.phone(), req.website(), req.linkedin(), null);
        }
        Lead lead = new Lead();
        lead.setOrganizationId(orgId);
        applyCreate(lead, req);
        applyDefaults(orgId, lead, req.pipelineId(), req.stageId(), req.assignedUserId(), req.sourceId());
        if (req.status() != null) lead.setStatus(LeadStatus.valueOf(req.status()));
        lead.setScore(scoring.score(orgId, lead));
        resolveTags(orgId, lead, req.tags());
        leads.save(lead);

        pipelineService.openStageEntry(lead);
        activities.record(orgId, ActivityType.LEAD_CREATED, lead.getId(), "Lead created", null,
            Map.of("source", lead.getSourceId() != null ? String.valueOf(lead.getSourceId()) : "manual"), CurrentUser.idOrNull());
        automationEngine.leadCreated(orgId, lead.getId());
        audit.log("LEAD_CREATE", "LEAD", lead.getId(), lead.getBusinessName(), null, Map.of("status", lead.getStatus().name()));
        return toItem(lead, orgId);
    }

    @Transactional
    public LeadItem update(UUID orgId, UUID leadId, UpdateLeadRequest req) {
        Lead lead = accessPolicy.loadVisible(orgId, leadId);
        Map<String, Object> before = diffSnapshot(lead);

        if (req.businessName() != null) lead.setBusinessName(req.businessName().trim());
        copyIfPresent(req.firstName(), lead::setFirstName);
        copyIfPresent(req.lastName(), lead::setLastName);
        copyIfPresent(req.jobTitle(), lead::setJobTitle);
        copyIfPresent(req.email(), lead::setEmail);
        copyIfPresent(req.secondaryEmail(), lead::setSecondaryEmail);
        copyIfPresent(req.phone(), lead::setPhone);
        copyIfPresent(req.whatsapp(), lead::setWhatsapp);
        copyIfPresent(req.website(), lead::setWebsite);
        copyIfPresent(req.linkedin(), lead::setLinkedin);
        copyIfPresent(req.country(), lead::setCountry);
        copyIfPresent(req.state(), lead::setState);
        copyIfPresent(req.city(), lead::setCity);
        copyIfPresent(req.address(), lead::setAddress);
        copyIfPresent(req.timezone(), lead::setTimezone);
        copyIfPresent(req.industry(), lead::setIndustry);
        copyIfPresent(req.businessType(), lead::setBusinessType);
        copyIfPresent(req.companySize(), lead::setCompanySize);
        if (req.employeesCount() != null) lead.setEmployeesCount(req.employeesCount());
        copyIfPresent(req.revenueRange(), lead::setRevenueRange);
        if (req.customFields() != null) {
            Map<String, Object> merged = lead.getCustomFields() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(lead.getCustomFields());
            merged.putAll(req.customFields());
            lead.setCustomFields(merged);
        }
        if (req.status() != null) {
            LeadStatus newStatus = LeadStatus.valueOf(req.status());
            if (newStatus != lead.getStatus()) {
                lead.setStatus(newStatus);
                activities.record(orgId, ActivityType.STATUS_CHANGE, lead.getId(), "Status changed to " + newStatus, null,
                    Map.of("from", lead.getStatus().name(), "to", newStatus.name()), CurrentUser.require().getId());
            }
        }
        if (req.sourceId() != null) lead.setSourceId(req.sourceId());
        if (req.nextFollowUpAt() != null) lead.setNextFollowUpAt(req.nextFollowUpAt());
        copyIfPresent(req.notes(), lead::setNotes);
        if (req.tags() != null) resolveTags(orgId, lead, req.tags());

        int newScore = scoring.score(orgId, lead);
        if (newScore != lead.getScore()) {
            activities.record(orgId, ActivityType.SCORE_CHANGE, lead.getId(), "Score changed to " + newScore, null,
                Map.of("from", lead.getScore(), "to", newScore), CurrentUser.require().getId());
            lead.setScore(newScore);
        }
        leads.save(lead);
        audit.log("LEAD_UPDATE", "LEAD", lead.getId(), lead.getBusinessName(), before, diffSnapshot(lead));
        return toItem(lead, orgId);
    }

    @Transactional
    public void delete(UUID orgId, UUID leadId) {
        Lead lead = accessPolicy.loadVisible(orgId, leadId);
        lead.setDeletedAt(Instant.now());
        leads.save(lead);
        audit.log("LEAD_DELETE", "LEAD", lead.getId(), lead.getBusinessName(), null, null);
    }

    @Transactional
    public LeadItem assign(UUID orgId, UUID leadId, UUID targetUserId) {
        Lead lead = accessPolicy.loadVisible(orgId, leadId);
        users.findById(targetUserId).filter(u -> u.getOrganizationId().equals(orgId))
            .orElseThrow(() -> ApiException.badRequest("Target user is not part of this organization"));
        UUID previous = lead.getAssignedUserId();
        lead.setAssignedUserId(targetUserId);
        leads.save(lead);
        activities.record(orgId, ActivityType.ASSIGNMENT_CHANGE, lead.getId(), "Lead assigned", null,
            Map.of("from", String.valueOf(previous), "to", String.valueOf(targetUserId)), CurrentUser.require().getId());
        notifications.notify(orgId, targetUserId, "LEAD_ASSIGNED", "New lead assigned: " + lead.getBusinessName(),
            "You have been assigned lead " + lead.getBusinessName(), "LEAD", lead.getId());
        audit.log("LEAD_ASSIGN", "LEAD", lead.getId(), lead.getBusinessName(),
            Map.of("assignedTo", String.valueOf(previous)), Map.of("assignedTo", String.valueOf(targetUserId)));
        return toItem(lead, orgId);
    }

    @Transactional
    public LeadItem changeStage(UUID orgId, UUID leadId, UUID stageId) {
        Lead lead = accessPolicy.loadVisible(orgId, leadId);
        var stage = stages.findById(stageId).orElseThrow(() -> ApiException.badRequest("Unknown stage"));
        if (lead.getStageId() != null && !lead.getPipelineId().equals(stage.getPipelineId())) {
            throw ApiException.badRequest("Stage does not belong to the lead's pipeline");
        }
        UUID from = lead.getStageId();
        if (stageId.equals(from)) return toItem(lead, orgId);

        if (lead.getStageId() == null) {
            lead.setPipelineId(stage.getPipelineId());
        }
        lead.setStageId(stageId);
        leads.save(lead);

        pipelineService.recordTransition(lead, from, stageId, CurrentUser.require().getId());
        activities.record(orgId, ActivityType.STAGE_CHANGE, lead.getId(), "Moved to " + stage.getName(), null,
            Map.of("from", String.valueOf(from), "to", stageId.toString()), CurrentUser.require().getId());
        automationEngine.stageChanged(orgId, lead.getId(), from, stageId);
        audit.log("LEAD_STAGE_CHANGE", "LEAD", lead.getId(), lead.getBusinessName(),
            Map.of("stage", String.valueOf(from)), Map.of("stage", stageId.toString()));
        return toItem(lead, orgId);
    }

    @Transactional
    public LeadItem setTags(UUID orgId, UUID leadId, List<String> tagNames) {
        Lead lead = accessPolicy.loadVisible(orgId, leadId);
        resolveTags(orgId, lead, tagNames);
        leads.save(lead);
        return toItem(lead, orgId);
    }

    @Transactional
    public void markContacted(UUID orgId, UUID leadId, Instant at) {
        leads.findById(leadId).filter(l -> l.getOrganizationId().equals(orgId)).ifPresent(l -> {
            l.setLastContactedAt(at);
            leads.save(l);
        });
    }

    // ---------- mapping ----------

    public LeadItem toItem(Lead l, UUID orgId) {
        String stageName = l.getStageId() != null ? stages.findById(l.getStageId()).map(s -> s.getName()).orElse(null) : null;
        String sourceName = l.getSourceId() != null ? sources.findById(l.getSourceId()).map(src -> src.getName()).orElse(null) : null;
        String assignedName = l.getAssignedUserId() != null ? users.findById(l.getAssignedUserId()).map(u -> u.displayName()).orElse(null) : null;
        return new LeadItem(l.getId(), l.getBusinessName(), l.getFirstName(), l.getLastName(), l.contactDisplayName(),
            l.getJobTitle(), l.getEmail(), l.getPhone(), l.getWhatsapp(), l.getWebsite(), l.getLinkedin(),
            l.getCountry(), l.getState(), l.getCity(), l.getAddress(), l.getTimezone(),
            l.getIndustry(), l.getBusinessType(), l.getCompanySize(), l.getEmployeesCount(), l.getRevenueRange(),
            l.getCustomFields(), l.getStatus().name(), l.getScore(), LeadScoringService.category(l.getScore()),
            l.getSourceId(), sourceName, l.getPipelineId(), l.getStageId(), stageName,
            l.getAssignedUserId(), assignedName, l.getLastContactedAt(), l.getNextFollowUpAt(),
            l.getTags().stream().map(Tag::getName).toList(), l.getNotes(), l.getCompanyId(), l.getContactId(),
            l.getCreatedAt(), l.getUpdatedAt());
    }

    // ---------- helpers ----------

    private void applyCreate(Lead lead, CreateLeadRequest req) {
        lead.setBusinessName(req.businessName().trim());
        copyIfPresent(req.firstName(), lead::setFirstName);
        copyIfPresent(req.lastName(), lead::setLastName);
        copyIfPresent(req.jobTitle(), lead::setJobTitle);
        copyIfPresent(req.email(), lead::setEmail);
        copyIfPresent(req.secondaryEmail(), lead::setSecondaryEmail);
        copyIfPresent(req.phone(), lead::setPhone);
        copyIfPresent(req.whatsapp(), lead::setWhatsapp);
        copyIfPresent(req.website(), lead::setWebsite);
        copyIfPresent(req.linkedin(), lead::setLinkedin);
        copyIfPresent(req.country(), lead::setCountry);
        copyIfPresent(req.state(), lead::setState);
        copyIfPresent(req.city(), lead::setCity);
        copyIfPresent(req.address(), lead::setAddress);
        copyIfPresent(req.timezone(), lead::setTimezone);
        copyIfPresent(req.industry(), lead::setIndustry);
        copyIfPresent(req.businessType(), lead::setBusinessType);
        copyIfPresent(req.companySize(), lead::setCompanySize);
        if (req.employeesCount() != null) lead.setEmployeesCount(req.employeesCount());
        copyIfPresent(req.revenueRange(), lead::setRevenueRange);
        if (req.customFields() != null) lead.setCustomFields(new LinkedHashMap<>(req.customFields()));
        if (req.nextFollowUpAt() != null) lead.setNextFollowUpAt(req.nextFollowUpAt());
        copyIfPresent(req.notes(), lead::setNotes);
    }

    private void applyDefaults(UUID orgId, Lead lead, UUID pipelineId, UUID stageId, UUID assignedUserId, UUID sourceId) {
        if (pipelineId != null) {
            lead.setPipelineId(pipelineId);
        } else {
            pipelines.findFirstByOrganizationIdAndIsDefaultTrue(orgId).ifPresent(p -> lead.setPipelineId(p.getId()));
        }
        if (stageId != null) {
            lead.setStageId(stageId);
        } else if (lead.getPipelineId() != null) {
            var stageList = stages.findByPipelineIdOrderByPositionAsc(lead.getPipelineId());
            if (!stageList.isEmpty()) lead.setStageId(stageList.get(0).getId());
        }
        if (assignedUserId != null) lead.setAssignedUserId(assignedUserId);
        else lead.setAssignedUserId(CurrentUser.idOrNull());
        lead.setSourceId(sourceId);
    }

    void resolveTags(UUID orgId, Lead lead, List<String> tagNames) {
        if (tagNames == null) return;
        Set<Tag> resolved = new LinkedHashSet<>();
        for (String name : tagNames) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) continue;
            Tag tag = tags.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .filter(t -> t.getName().equalsIgnoreCase(trimmed)).findFirst()
                .orElseGet(() -> {
                    Tag t = new Tag();
                    t.setOrganizationId(orgId);
                    t.setName(trimmed);
                    return tags.save(t);
                });
            resolved.add(tag);
        }
        lead.getTags().clear();
        lead.getTags().addAll(resolved);
    }

    private Map<String, Object> diffSnapshot(Lead l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("businessName", l.getBusinessName());
        m.put("email", l.getEmail());
        m.put("phone", l.getPhone());
        m.put("status", l.getStatus().name());
        m.put("stageId", String.valueOf(l.getStageId()));
        m.put("assignedUserId", String.valueOf(l.getAssignedUserId()));
        m.put("score", l.getScore());
        return m;
    }

    private void copyIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null) setter.accept(value.isBlank() ? null : value.trim());
    }
}
