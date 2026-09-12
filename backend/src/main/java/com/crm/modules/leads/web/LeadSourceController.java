package com.crm.modules.leads.web;

import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.leads.domain.LeadSource;
import com.crm.modules.leads.repo.LeadSourceRepository;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lead-sources")
@RequiredArgsConstructor
@Tag(name = "Lead Sources")
public class LeadSourceController {

    private final LeadSourceRepository sources;

    public record SourceRequest(@NotBlank String name, String key, String description) {}

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.SOURCE_VIEW + "')")
    public List<LeadSource> list() {
        return sources.findByOrganizationIdOrderByNameAsc(CurrentUser.require().getOrganizationId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.SOURCE_UPDATE + "')")
    public LeadSource create(@RequestBody SourceRequest request) {
        LeadSource s = new LeadSource();
        s.setOrganizationId(CurrentUser.require().getOrganizationId());
        s.setName(request.name().trim());
        s.setKey(request.key() != null ? request.key().trim().toUpperCase().replaceAll("[^A-Z0-9_]+", "_") : slug(request.name()));
        s.setDescription(request.description());
        return sources.save(s);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.SOURCE_UPDATE + "')")
    public LeadSource update(@PathVariable UUID id, @RequestBody SourceRequest request) {
        LeadSource s = sources.findById(id).orElseThrow();
        s.setName(request.name().trim());
        s.setDescription(request.description());
        return sources.save(s);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.SOURCE_UPDATE + "')")
    public void delete(@PathVariable UUID id) {
        sources.deleteById(id);
    }

    private String slug(String name) {
        return name.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").substring(0, Math.min(30, name.length()));
    }
}
