package com.crm.modules.leads.web;

import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.leads.dto.LeadDtos.ConvertLeadRequest;
import com.crm.modules.leads.service.LeadConversionService;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
@Tag(name = "Lead Conversion")
public class LeadLifecycleController {

    private final LeadConversionService conversionService;

    @PostMapping("/{id}/convert")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_CONVERT + "')")
    public Map<String, Object> convert(@PathVariable UUID id, @Valid @RequestBody(required = false) ConvertLeadRequest request) {
        var result = conversionService.convert(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(),
            id, request == null ? new ConvertLeadRequest(null, null, null, null, null) : request);
        return Map.of(
            "clientId", result.clientId(),
            "companyId", result.companyId(),
            "contactId", result.contactId(),
            "dealId", result.dealId() == null ? "" : result.dealId());
    }
}
