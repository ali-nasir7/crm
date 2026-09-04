package com.crm.modules.identity.web;

import com.crm.common.api.PageResponse;
import com.crm.modules.identity.dto.IdentityDtos.*;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.identity.service.UserService;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.USER_VIEW + "')")
    public PageResponse<UserItem> list(@RequestParam(required = false) String query,
                                       @RequestParam(required = false) String role,
                                       @RequestParam(required = false) UUID teamId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "25") int size) {
        return userService.list(CurrentUser.require().getOrganizationId(), query, role, teamId, page, size);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.USER_CREATE + "')")
    public UserItem create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(CurrentUser.require().getOrganizationId(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.USER_VIEW + "')")
    public UserItem get(@PathVariable UUID id) {
        return userService.get(CurrentUser.require().getOrganizationId(), id);
    }

    /** Admin password reset: returns the generated temp password exactly once (audit-logged). */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('" + PermissionKeys.USER_UPDATE + "')")
    public Map<String, String> resetPassword(@PathVariable UUID id,
                                             @RequestBody(required = false) Map<String, Object> body) {
        boolean sendEmail = body != null && Boolean.TRUE.equals(body.get("sendEmail"));
        return userService.resetPassword(CurrentUser.require().getOrganizationId(), id, sendEmail);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.USER_UPDATE + "')")
    public UserItem update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(CurrentUser.require().getOrganizationId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.USER_DELETE + "')")
    public void delete(@PathVariable UUID id) {
        userService.delete(CurrentUser.require().getOrganizationId(), id);
    }
}
