package com.crm.modules.leads.web;

import com.crm.common.api.PageResponse;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.leads.dto.LeadDtos.*;
import com.crm.modules.leads.service.*;
import com.crm.security.CurrentUser;
import com.crm.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
@Tag(name = "Leads")
public class LeadController {

    private final LeadService leadService;
    private final LeadAccessPolicy accessPolicy;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_VIEW + "')")
    public PageResponse<LeadItem> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID pipelineId,
        @RequestParam(required = false) UUID stageId,
        @RequestParam(required = false) UUID assignedTo,
        @RequestParam(required = false) UUID teamId,
        @RequestParam(required = false) UUID sourceId,
        @RequestParam(required = false) String country,
        @RequestParam(required = false) String city,
        @RequestParam(required = false) String state,
        @RequestParam(required = false) String industry,
        @RequestParam(required = false) String companySize,
        @RequestParam(required = false) List<String> tags,
        @RequestParam(required = false) Integer minScore,
        @RequestParam(required = false) Integer maxScore,
        @RequestParam(required = false) Boolean uncontacted,
        @RequestParam(required = false) Boolean hasEmail,
        @RequestParam(required = false) Boolean hasPhone,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant lastContactedBefore,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant lastContactedAfter,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant nextFollowUpBefore,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant nextFollowUpAfter,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
        @RequestParam Map<String, String> allParams,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size,
        @RequestParam(required = false) String sort) {

        Map<String, Object> custom = new java.util.LinkedHashMap<>();
        allParams.forEach((k, v) -> { if (k.startsWith("cf.")) custom.put(k.substring(3), v); });

        UserPrincipal principal = CurrentUser.require();
        var filters = new LeadSpecs.LeadFilters(q, status, pipelineId, stageId, assignedTo, teamId, sourceId,
            country, city, state, industry, companySize, tags, minScore, maxScore, uncontacted, hasEmail, hasPhone,
            lastContactedBefore, lastContactedAfter, nextFollowUpBefore, nextFollowUpAfter, createdFrom, createdTo, custom);
        return leadService.list(principal, principal.getOrganizationId(), filters, page, size, sort);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_CREATE + "')")
    public LeadItem create(@Valid @RequestBody CreateLeadRequest request) {
        return leadService.create(CurrentUser.require().getOrganizationId(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_VIEW + "')")
    public LeadItem get(@PathVariable UUID id) {
        return leadService.get(CurrentUser.require().getOrganizationId(), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_UPDATE + "')")
    public LeadItem update(@PathVariable UUID id, @Valid @RequestBody UpdateLeadRequest request) {
        return leadService.update(CurrentUser.require().getOrganizationId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_DELETE + "')")
    public void delete(@PathVariable UUID id) {
        leadService.delete(CurrentUser.require().getOrganizationId(), id);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_ASSIGN + "')")
    public LeadItem assign(@PathVariable UUID id, @RequestBody AssignRequest request) {
        return leadService.assign(CurrentUser.require().getOrganizationId(), id, request.userId());
    }

    @PostMapping("/{id}/stage")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_UPDATE + "')")
    public LeadItem changeStage(@PathVariable UUID id, @RequestBody StageRequest request) {
        return leadService.changeStage(CurrentUser.require().getOrganizationId(), id, request.stageId());
    }

    @PostMapping("/{id}/tags")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_UPDATE + "')")
    public LeadItem setTags(@PathVariable UUID id, @RequestBody TagsRequest request) {
        return leadService.setTags(CurrentUser.require().getOrganizationId(), id, request.tags());
    }
}
