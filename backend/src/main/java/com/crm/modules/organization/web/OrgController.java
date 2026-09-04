package com.crm.modules.organization.web;

import com.crm.security.CurrentUser;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.organization.service.SettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Organization & Settings")
public class OrgController {

    private final SettingsService settingsService;

    @GetMapping("/org")
    @PreAuthorize("hasAuthority('" + PermissionKeys.ORG_VIEW + "')")
    public Map<String, Object> org() {
        return Map.of("id", CurrentUser.require().getOrganizationId());
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('" + PermissionKeys.SETTINGS_VIEW + "')")
    public Map<String, Object> settings() {
        return settingsService.get(CurrentUser.require().getOrganizationId());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('" + PermissionKeys.SETTINGS_UPDATE + "')")
    public Map<String, Object> updateSettings(@RequestBody Map<String, Object> patch) {
        return settingsService.update(CurrentUser.require().getOrganizationId(), patch);
    }
}
