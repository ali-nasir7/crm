package com.crm.modules.tasks.web;

import com.crm.common.api.PageResponse;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.tasks.dto.TaskDtos.*;
import com.crm.modules.tasks.service.TaskService;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Tasks")
public class TaskController {

    private final TaskService taskService;

    @PostMapping("/tasks")
    @PreAuthorize("hasAuthority('" + PermissionKeys.TASK_CREATE + "')")
    public TaskItem create(@Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), request);
    }

    @PostMapping("/leads/{leadId}/tasks")
    @PreAuthorize("hasAuthority('" + PermissionKeys.TASK_CREATE + "')")
    public TaskItem createForLead(@PathVariable UUID leadId, @Valid @RequestBody CreateTaskRequest request) {
        CreateTaskRequest withLead = new CreateTaskRequest(request.title(), request.description(), leadId,
            request.companyId(), request.contactId(), request.taskType(), request.assignedUserId(), request.dueAt(), request.priority());
        return taskService.create(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), withLead);
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('" + PermissionKeys.TASK_VIEW + "')")
    public PageResponse<TaskItem> list(@RequestParam(required = false) UUID assignee,
                                       @RequestParam(required = false) UUID leadId,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dueBefore,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dueAfter,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "25") int size) {
        UUID orgId = CurrentUser.require().getOrganizationId();
        return taskService.list(orgId, CurrentUser.require().getId(), assignee, status, dueBefore, dueAfter, leadId, page, size);
    }

    @PutMapping("/tasks/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.TASK_UPDATE + "')")
    public TaskItem update(@PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(CurrentUser.require().getOrganizationId(), id, request);
    }

    @PostMapping("/tasks/{id}/complete")
    @PreAuthorize("hasAuthority('" + PermissionKeys.TASK_UPDATE + "')")
    public TaskItem complete(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        return taskService.complete(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), id,
            body == null ? null : body.get("note"));
    }

    @DeleteMapping("/tasks/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.TASK_DELETE + "')")
    public void delete(@PathVariable UUID id) {
        taskService.delete(CurrentUser.require().getOrganizationId(), id);
    }
}
