package com.crm.modules.leads.web;

import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.leads.domain.CustomFieldDef;
import com.crm.modules.leads.dto.LeadDtos.CustomFieldRequest;
import com.crm.modules.leads.repo.CustomFieldDefRepository;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/custom-fields")
@RequiredArgsConstructor
@Tag(name = "Custom Fields")
public class CustomFieldController {

    private final CustomFieldDefRepository defs;

    @GetMapping
    public List<CustomFieldDef> list() {
        return defs.findByOrganizationIdOrderByPositionAsc(CurrentUser.require().getOrganizationId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.SETTINGS_UPDATE + "')")
    public CustomFieldDef create(@Valid @RequestBody CustomFieldRequest request) {
        var orgId = CurrentUser.require().getOrganizationId();
        CustomFieldDef def = new CustomFieldDef();
        def.setOrganizationId(orgId);
        def.setKey(request.key().trim().replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase());
        def.setLabel(request.label().trim());
        def.setType(request.type().toUpperCase());
        def.setOptions(request.options());
        def.setPosition(request.position());
        return defs.save(def);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.SETTINGS_UPDATE + "')")
    public void delete(@PathVariable UUID id) {
        defs.deleteById(id);
    }
}
