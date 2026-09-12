package com.crm.modules.leads.web;

import com.crm.common.api.ApiException;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.leads.domain.SavedView;
import com.crm.modules.leads.dto.LeadDtos.SavedViewRequest;
import com.crm.modules.leads.repo.SavedViewRepository;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lead-views")
@RequiredArgsConstructor
@Tag(name = "Saved Views")
public class SavedViewController {

    private final SavedViewRepository views;

    public record ViewItem(UUID id, String name, boolean shared, boolean mine, Object filters, String sort) {}

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_VIEW + "')")
    public List<ViewItem> list() {
        UUID orgId = CurrentUser.require().getOrganizationId();
        UUID userId = CurrentUser.require().getId();
        return views.findByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
            .filter(v -> v.isShared() || v.getOwnerId().equals(userId))
            .map(v -> new ViewItem(v.getId(), v.getName(), v.isShared(), v.getOwnerId().equals(userId), v.getFilters(), v.getSort()))
            .toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_VIEW + "')")
    public ViewItem create(@Valid @RequestBody SavedViewRequest request) {
        var principal = CurrentUser.require();
        SavedView v = new SavedView();
        v.setOrganizationId(principal.getOrganizationId());
        v.setOwnerId(principal.getId());
        v.setName(request.name().trim());
        v.setShared(request.shared());
        v.setFilters(request.filters());
        v.setSort(request.sort());
        views.save(v);
        return new ViewItem(v.getId(), v.getName(), v.isShared(), true, v.getFilters(), v.getSort());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_VIEW + "')")
    public void delete(@PathVariable UUID id) {
        var principal = CurrentUser.require();
        SavedView v = views.findById(id).filter(x -> x.getOrganizationId().equals(principal.getOrganizationId()))
            .orElseThrow(() -> ApiException.notFound("View not found"));
        if (!v.getOwnerId().equals(principal.getId())) throw ApiException.forbidden("Not your view");
        views.delete(v);
    }
}
