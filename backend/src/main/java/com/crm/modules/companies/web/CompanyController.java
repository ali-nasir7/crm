package com.crm.modules.companies.web;

import com.crm.common.api.PageResponse;
import com.crm.modules.companies.dto.CompanyDtos.*;
import com.crm.modules.companies.service.CompanyService;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@Tag(name = "Companies")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.COMPANY_VIEW + "')")
    public PageResponse<CompanyItem> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) String industry,
                                          @RequestParam(required = false) String country,
                                          @RequestParam(required = false) String city,
                                          @RequestParam(required = false) UUID ownerId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "25") int size) {
        return companyService.list(CurrentUser.require().getOrganizationId(), q, industry, country, city, ownerId, page, size);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.COMPANY_CREATE + "')")
    public CompanyItem create(@Valid @RequestBody CreateCompanyRequest request) {
        return companyService.create(CurrentUser.require().getOrganizationId(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.COMPANY_VIEW + "')")
    public CompanyItem get(@PathVariable UUID id) {
        return companyService.get(CurrentUser.require().getOrganizationId(), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.COMPANY_UPDATE + "')")
    public CompanyItem update(@PathVariable UUID id, @Valid @RequestBody UpdateCompanyRequest request) {
        return companyService.update(CurrentUser.require().getOrganizationId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.COMPANY_DELETE + "')")
    public void delete(@PathVariable UUID id) {
        companyService.delete(CurrentUser.require().getOrganizationId(), id);
    }
}
