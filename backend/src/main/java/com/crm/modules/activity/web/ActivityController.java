package com.crm.modules.activity.web;

import com.crm.common.api.PageResponse;
import com.crm.modules.activity.dto.ActivityDtos.*;
import com.crm.modules.activity.service.ActivityService;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.leads.service.LeadAccessPolicy;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leads/{leadId}/activities")
@RequiredArgsConstructor
@Tag(name = "Activities")
public class ActivityController {

    private final ActivityService activityService;
    private final LeadAccessPolicy accessPolicy;

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_UPDATE + "')")
    public ActivityItem addNote(@PathVariable UUID leadId, @Valid @RequestBody CreateNoteRequest request) {
        UUID orgId = CurrentUser.require().getOrganizationId();
        accessPolicy.assertCanAccess(orgId, leadId);
        return activityService.addNote(orgId, CurrentUser.require().getId(), leadId, request.body());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_VIEW + "')")
    public PageResponse<ActivityItem> timeline(@PathVariable UUID leadId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "25") int size) {
        UUID orgId = CurrentUser.require().getOrganizationId();
        accessPolicy.assertCanAccess(orgId, leadId);
        return activityService.timeline(orgId, leadId, page, size);
    }
}
