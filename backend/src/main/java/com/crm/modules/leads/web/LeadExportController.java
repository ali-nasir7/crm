package com.crm.modules.leads.web;

import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.leads.service.LeadAccessPolicy;
import com.crm.modules.leads.service.LeadSpecs;
import com.crm.modules.audit.service.AuditService;
import com.crm.security.CurrentUser;
import com.crm.common.util.CsvUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
@Tag(name = "Lead Export")
public class LeadExportController {

    private final LeadAccessPolicy accessPolicy;
    private final com.crm.modules.leads.repo.LeadRepository leads;
    private final AuditService audit;

    /** CSV export respecting the caller's visibility scope. Export is audit-logged (who/when/how many). */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_EXPORT + "')")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) java.util.UUID assignedTo,
                       @RequestParam(required = false) List<String> tags,
                       @RequestParam(required = false) Integer minScore) throws IOException {
        var principal = CurrentUser.require();
        UUID orgId = principal.getOrganizationId();
        var spec = accessPolicy.visibility(orgId, principal)
            .and(LeadSpecs.from(new LeadSpecs.LeadFilters(q, status, null, null, assignedTo, null, null,
                null, null, null, null, null, tags, minScore, null, null, null, null,
                null, null, null, null, null, null, null)));
        var all = leads.findAll(spec, org.springframework.data.domain.PageRequest.of(0, 10_000)).getContent();

        List<String> header = List.of("Business Name", "First Name", "Last Name", "Job Title", "Email", "Phone",
            "Website", "LinkedIn", "Country", "City", "Industry", "Company Size", "Status", "Score", "Owner", "Created");
        List<List<Object>> rows = all.stream().map(l -> List.<Object>of(
            nz(l.getBusinessName()), nz(l.getFirstName()), nz(l.getLastName()), nz(l.getJobTitle()),
            nz(l.getEmail()), nz(l.getPhone()), nz(l.getWebsite()), nz(l.getLinkedin()),
            nz(l.getCountry()), nz(l.getCity()), nz(l.getIndustry()), nz(l.getCompanySize()),
            l.getStatus().name(), l.getScore(),
            l.getAssignedUserId() != null ? accessPolicyUserName(l.getAssignedUserId()) : "",
            String.valueOf(l.getCreatedAt()))).toList();

        audit.log("LEAD_EXPORT", "LEAD", null, "CSV export", null, java.util.Map.of("rows", rows.size()));
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=leads-export.csv");
        response.getWriter().write(CsvUtil.write(header, rows));
    }

    private final com.crm.modules.identity.repo.UserRepository users;
    private String accessPolicyUserName(java.util.UUID id) {
        return users.findById(id).map(u -> u.displayName()).orElse("");
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
