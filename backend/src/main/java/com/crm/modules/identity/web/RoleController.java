package com.crm.modules.identity.web;

import com.crm.modules.identity.dto.IdentityDtos.*;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.identity.service.RoleService;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Roles & Permissions")
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('" + PermissionKeys.ROLE_VIEW + "')")
    public List<RoleItem> list() { return roleService.list(CurrentUser.require().getOrganizationId()); }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('" + PermissionKeys.ROLE_CREATE + "')")
    public RoleItem create(@Valid @RequestBody RoleRequest request) { return roleService.create(CurrentUser.require().getOrganizationId(), request); }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.ROLE_UPDATE + "')")
    public RoleItem update(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) { return roleService.update(CurrentUser.require().getOrganizationId(), id, request); }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.ROLE_DELETE + "')")
    public void delete(@PathVariable UUID id) { roleService.delete(CurrentUser.require().getOrganizationId(), id); }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('" + PermissionKeys.ROLE_VIEW + "')")
    public List<PermissionItem> permissions() { return roleService.allPermissions(); }
}
