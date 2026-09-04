package com.crm.modules.campaigns.web;

import com.crm.common.api.PageResponse;
import com.crm.modules.campaigns.service.CampaignService;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.CAMPAIGN_VIEW + "')")
    public PageResponse<CampaignService.CampaignItem> list(@RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "25") int size) {
        return campaignService.list(CurrentUser.require().getOrganizationId(), page, size);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.CAMPAIGN_CREATE + "')")
    public CampaignService.CampaignItem create(@Valid @RequestBody CampaignService.CreateCampaignRequest request) {
        return campaignService.create(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CAMPAIGN_VIEW + "')")
    public CampaignService.CampaignItem get(@PathVariable UUID id) {
        return campaignService.get(CurrentUser.require().getOrganizationId(), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CAMPAIGN_UPDATE + "')")
    public CampaignService.CampaignItem update(@PathVariable UUID id, @Valid @RequestBody CampaignService.CreateCampaignRequest request) {
        return campaignService.update(CurrentUser.require().getOrganizationId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CAMPAIGN_UPDATE + "')")
    public void delete(@PathVariable UUID id) {
        campaignService.delete(CurrentUser.require().getOrganizationId(), id);
    }

    @PostMapping("/{id}/recipients")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CAMPAIGN_UPDATE + "')")
    public Map<String, Object> addRecipients(@PathVariable UUID id, @RequestBody CampaignService.AddRecipientsRequest request) {
        int added = campaignService.addRecipients(CurrentUser.require().getOrganizationId(), id, request.leadIds());
        return Map.of("added", added);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CAMPAIGN_SEND + "')")
    public CampaignService.CampaignItem start(@PathVariable UUID id) {
        return campaignService.start(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), id);
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CAMPAIGN_SEND + "')")
    public CampaignService.CampaignItem pause(@PathVariable UUID id) {
        return campaignService.pause(CurrentUser.require().getOrganizationId(), id);
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CAMPAIGN_SEND + "')")
    public CampaignService.CampaignItem resume(@PathVariable UUID id) {
        return campaignService.resume(CurrentUser.require().getOrganizationId(), id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CAMPAIGN_SEND + "')")
    public CampaignService.CampaignItem cancel(@PathVariable UUID id) {
        return campaignService.cancel(CurrentUser.require().getOrganizationId(), id);
    }

    @GetMapping("/{id}/recipients")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CAMPAIGN_VIEW + "')")
    public PageResponse<CampaignService.RecipientItem> recipients(@PathVariable UUID id,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "50") int size) {
        return campaignService.recipients(CurrentUser.require().getOrganizationId(), id, page, size);
    }
}
