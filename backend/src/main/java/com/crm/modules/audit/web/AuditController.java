package com.crm.modules.audit.web;

import com.crm.common.api.PageResponse;
import com.crm.modules.audit.service.AuditService;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.AUDIT_VIEW + "')")
    public PageResponse<Map<String, Object>> list(
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) UUID entityId,
        @RequestParam(required = false) UUID actorId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size) {
        return auditService.list(CurrentUser.require().getOrganizationId(), action, entityType, entityId, actorId, from, to, page, size);
    }
}
