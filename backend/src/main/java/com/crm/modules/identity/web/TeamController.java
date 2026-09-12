package com.crm.modules.identity.web;

import com.crm.modules.identity.dto.IdentityDtos.*;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.identity.service.TeamService;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
@Tag(name = "Teams")
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.TEAM_VIEW + "')")
    public List<TeamItem> list() { return teamService.list(CurrentUser.require().getOrganizationId()); }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.TEAM_CREATE + "')")
    public TeamItem create(@Valid @RequestBody TeamRequest request) { return teamService.create(CurrentUser.require().getOrganizationId(), request); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.TEAM_UPDATE + "')")
    public TeamItem update(@PathVariable UUID id, @Valid @RequestBody TeamRequest request) { return teamService.update(CurrentUser.require().getOrganizationId(), id, request); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.TEAM_DELETE + "')")
    public void delete(@PathVariable UUID id) { teamService.delete(CurrentUser.require().getOrganizationId(), id); }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('" + PermissionKeys.TEAM_UPDATE + "')")
    public TeamItem addMembers(@PathVariable UUID id, @Valid @RequestBody MemberRequest request) { return teamService.addMembers(CurrentUser.require().getOrganizationId(), id, request); }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.TEAM_UPDATE + "')")
    public TeamItem removeMember(@PathVariable UUID id, @PathVariable UUID userId) { return teamService.removeMember(CurrentUser.require().getOrganizationId(), id, userId); }
}
