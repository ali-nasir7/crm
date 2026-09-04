package com.crm.modules.deals.web;

import com.crm.common.api.PageResponse;
import com.crm.modules.deals.dto.DealDtos.*;
import com.crm.modules.deals.service.DealService;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deals")
@RequiredArgsConstructor
@Tag(name = "Deals")
public class DealController {

    private final DealService dealService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.DEAL_VIEW + "')")
    public PageResponse<DealItem> list(@RequestParam(required = false) String status,
                                       @RequestParam(required = false) UUID ownerId,
                                       @RequestParam(required = false) UUID stageId,
                                       @RequestParam(required = false) UUID pipelineId,
                                       @RequestParam(required = false) UUID leadId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "25") int size) {
        return dealService.list(CurrentUser.require(), CurrentUser.require().getOrganizationId(), status, ownerId, stageId, pipelineId, leadId, page, size);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('" + PermissionKeys.DEAL_VIEW + "')")
    public DealSummary summary() {
        return dealService.summary(CurrentUser.require().getOrganizationId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.DEAL_CREATE + "')")
    public DealItem create(@Valid @RequestBody CreateDealRequest request) {
        return dealService.create(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.DEAL_VIEW + "')")
    public DealItem get(@PathVariable UUID id) {
        return dealService.get(CurrentUser.require().getOrganizationId(), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.DEAL_UPDATE + "')")
    public DealItem update(@PathVariable UUID id, @Valid @RequestBody UpdateDealRequest request) {
        return dealService.update(CurrentUser.require().getOrganizationId(), id, request);
    }

    @PostMapping("/{id}/stage")
    @PreAuthorize("hasAuthority('" + PermissionKeys.DEAL_UPDATE + "')")
    public DealItem changeStage(@PathVariable UUID id, @RequestBody StageRequest request) {
        return dealService.changeStage(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), id, request.stageId());
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionKeys.DEAL_UPDATE + "')")
    public DealItem changeStatus(@PathVariable UUID id, @RequestBody StatusRequest request) {
        return dealService.changeStatus(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), id, request.status(), request.lostReason());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.DEAL_DELETE + "')")
    public void delete(@PathVariable UUID id) {
        dealService.delete(CurrentUser.require().getOrganizationId(), id);
    }
}
