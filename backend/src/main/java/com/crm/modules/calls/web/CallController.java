package com.crm.modules.calls.web;

import com.crm.common.api.PageResponse;
import com.crm.modules.calls.dto.CallDtos.*;
import com.crm.modules.calls.service.CallService;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Calls")
public class CallController {

    private final CallService callService;

    @PostMapping("/leads/{leadId}/calls")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CALL_CREATE + "')")
    public CallItem logCall(@PathVariable UUID leadId, @Valid @RequestBody CreateCallRequest request) {
        return callService.logCall(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), leadId, request);
    }

    @GetMapping("/leads/{leadId}/calls")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CALL_VIEW + "')")
    public PageResponse<CallItem> leadCalls(@PathVariable UUID leadId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "25") int size) {
        return callService.list(CurrentUser.require().getOrganizationId(), leadId, null, page, size);
    }

    @GetMapping("/calls")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CALL_VIEW + "')")
    public PageResponse<CallItem> allCalls(@RequestParam(required = false) UUID userId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "25") int size) {
        return callService.list(CurrentUser.require().getOrganizationId(), null, userId, page, size);
    }
}
