package com.crm.modules.bulk.web;

import com.crm.common.api.PageResponse;
import com.crm.modules.bulk.service.BulkService;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leads/bulk")
@RequiredArgsConstructor
@Tag(name = "Bulk Operations")
public class BulkController {

    private final BulkService bulkService;

    public record BulkRequest(String action, List<UUID> leadIds, Map<String, Object> params) {}

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_UPDATE + "')")
    public Map<String, Object> enqueue(@RequestBody BulkRequest request) {
        UUID orgId = CurrentUser.require().getOrganizationId();
        UUID userId = CurrentUser.require().getId();
        if ("DELETE".equalsIgnoreCase(request.action()) && !CurrentUser.require().hasPermission(PermissionKeys.LEAD_DELETE)) {
            throw com.crm.common.api.ApiException.forbidden("Missing permission: " + PermissionKeys.LEAD_DELETE);
        }
        var job = bulkService.enqueue(orgId, userId, request.action(), request.leadIds(), request.params());
        bulkService.run(orgId, job.id());
        return Map.of("jobId", job.id(), "status", job.status());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_VIEW + "')")
    public PageResponse<BulkService.BulkJobItem> jobs(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return bulkService.list(CurrentUser.require().getOrganizationId(), page, size);
    }
}
