package com.crm.modules.tasks.service;

import com.crm.common.api.PageResponse;
import com.crm.modules.activity.domain.ActivityType;
import com.crm.modules.activity.service.ActivityService;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.leads.domain.Lead;
import com.crm.modules.notifications.service.NotificationService;
import com.crm.modules.tasks.domain.Task;
import com.crm.modules.tasks.dto.TaskDtos.*;
import com.crm.modules.tasks.repo.TaskRepository;
import com.crm.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TaskService {

    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private static final Set<String> STATUSES = Set.of("OPEN", "COMPLETED", "CANCELLED");

    private final TaskRepository tasks;
    private final UserRepository users;
    private final ActivityService activities;
    private final NotificationService notifications;

    @Transactional
    public TaskItem create(UUID orgId, UUID actorId, CreateTaskRequest req) {
        UUID assignee = req.assignedUserId() != null ? req.assignedUserId() : actorId;
        users.findById(assignee).filter(u -> u.getOrganizationId().equals(orgId))
            .orElseThrow(() -> com.crm.common.api.ApiException.badRequest("Assignee is not part of this organization"));
        if (req.dueAt() == null) throw com.crm.common.api.ApiException.badRequest("Due date is required");

        Task t = new Task();
        t.setOrganizationId(orgId);
        t.setTitle(req.title().trim());
        t.setDescription(req.description());
        t.setLeadId(req.leadId());
        t.setCompanyId(req.companyId());
        t.setContactId(req.contactId());
        t.setTaskType(req.taskType() == null ? "CUSTOM" : req.taskType().toUpperCase());
        t.setAssignedUserId(assignee);
        t.setCreatedBy(actorId);
        t.setDueAt(req.dueAt());
        t.setPriority(req.priority() == null ? "MEDIUM" : req.priority().toUpperCase());
        if (!PRIORITIES.contains(t.getPriority())) throw com.crm.common.api.ApiException.badRequest("Invalid priority");
        tasks.save(t);

        activities.record(orgId, ActivityType.TASK_CREATED, req.leadId(), "Task: " + t.getTitle(), null,
            Map.of("dueAt", t.getDueAt().toString(), "priority", t.getPriority()), actorId);
        if (!assignee.equals(actorId)) {
            notifications.notify(orgId, assignee, "TASK_ASSIGNED", "New task: " + t.getTitle(),
                "Due " + t.getDueAt(), "TASK", t.getId());
        }
        return toItem(t);
    }

    @Transactional
    public void createFollowUpFromCall(UUID orgId, UUID actorId, Lead lead, String nextAction, Instant followUpAt) {
        Task t = new Task();
        t.setOrganizationId(orgId);
        t.setTitle(nextAction == null || nextAction.isBlank() ? "Follow up: " + lead.getBusinessName() : nextAction);
        t.setTaskType("FOLLOW_UP");
        t.setLeadId(lead.getId());
        t.setCompanyId(lead.getCompanyId());
        t.setAssignedUserId(lead.getAssignedUserId() != null ? lead.getAssignedUserId() : actorId);
        t.setDueAt(followUpAt);
        t.setPriority("MEDIUM");
        tasks.save(t);
        activities.record(orgId, ActivityType.FOLLOW_UP, lead.getId(), "Follow-up scheduled", null,
            Map.of("dueAt", followUpAt.toString()), actorId);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskItem> list(UUID orgId, UUID userId, UUID assignee, String status,
                                       Instant dueBefore, Instant dueAfter, UUID leadId, int page, int size) {
        Specification<Task> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("organizationId"), orgId));
            if (assignee != null) ps.add(cb.equal(root.get("assignedUserId"), assignee));
            if (leadId != null) ps.add(cb.equal(root.get("leadId"), leadId));
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), status.toUpperCase()));
            if (dueBefore != null) ps.add(cb.lessThanOrEqualTo(root.get("dueAt"), dueBefore));
            if (dueAfter != null) ps.add(cb.greaterThanOrEqualTo(root.get("dueAt"), dueAfter));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<TaskItem> result = tasks.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by("dueAt")))
            .map(this::toItem);
        return PageResponse.of(result.getContent(), result.getPageable(), result.getTotalElements());
    }

    @Transactional
    public TaskItem complete(UUID orgId, UUID actorId, UUID taskId, String note) {
        Task t = find(orgId, taskId);
        t.setStatus("COMPLETED");
        t.setCompletedAt(Instant.now());
        t.setCompletionNote(note);
        tasks.save(t);
        activities.record(orgId, ActivityType.TASK_COMPLETED, t.getLeadId(), "Completed: " + t.getTitle(), note,
            null, actorId);
        return toItem(t);
    }

    @Transactional
    public TaskItem update(UUID orgId, UUID taskId, UpdateTaskRequest req) {
        Task t = find(orgId, taskId);
        if (req.title() != null) t.setTitle(req.title().trim());
        if (req.description() != null) t.setDescription(req.description());
        if (req.dueAt() != null) t.setDueAt(req.dueAt());
        if (req.priority() != null) {
            String p = req.priority().toUpperCase();
            if (!PRIORITIES.contains(p)) throw com.crm.common.api.ApiException.badRequest("Invalid priority");
            t.setPriority(p);
        }
        if (req.status() != null) {
            String s = req.status().toUpperCase();
            if (!STATUSES.contains(s)) throw com.crm.common.api.ApiException.badRequest("Invalid status");
            t.setStatus(s);
            if ("COMPLETED".equals(s) && t.getCompletedAt() == null) {
                t.setCompletedAt(Instant.now());
                activities.record(orgId, ActivityType.TASK_COMPLETED, t.getLeadId(), "Completed: " + t.getTitle(), null, null, CurrentUser.idOrNull());
            }
        }
        if (req.completionNote() != null) t.setCompletionNote(req.completionNote());
        tasks.save(t);
        return toItem(t);
    }

    @Transactional
    public void delete(UUID orgId, UUID taskId) {
        tasks.delete(find(orgId, taskId));
    }

    /** Overdue tasks of a single organization (automation scanner). */
    @Transactional(readOnly = true)
    public List<Task> overdue(UUID orgId, int limit) {
        return tasks.findAll((root, cq, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId),
                cb.equal(root.get("status"), "OPEN"),
                cb.lessThan(root.get("dueAt"), Instant.now())),
            PageRequest.of(0, limit, Sort.by("dueAt"))).getContent();
    }

    private Task find(UUID orgId, UUID id) {
        return tasks.findById(id).filter(t -> t.getOrganizationId().equals(orgId))
            .orElseThrow(() -> com.crm.common.api.ApiException.notFound("Task not found"));
    }

    public TaskItem toItem(Task t) {
        return new TaskItem(t.getId(), t.getTitle(), t.getDescription(), t.getLeadId(), null, t.getTaskType(),
            t.getAssignedUserId(), users.findById(t.getAssignedUserId()).map(u -> u.displayName()).orElse(null),
            t.getCreatedBy(), t.getDueAt(), t.getPriority(), t.getStatus(), t.getCompletedAt(),
            t.getCompletionNote(), t.getCreatedAt());
    }
}
