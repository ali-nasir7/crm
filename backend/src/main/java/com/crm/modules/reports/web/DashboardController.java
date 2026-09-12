package com.crm.modules.reports.web;

import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.reports.service.DashboardService;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboards")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/executive")
    @PreAuthorize("hasAuthority('" + PermissionKeys.REPORT_VIEW + "')")
    public Map<String, Object> executive(@RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to) {
        var range = from != null && to != null ? new DashboardService.RangeParam(from, to) : DashboardService.RangeParam.last(30);
        return dashboardService.executive(CurrentUser.require().getOrganizationId(), range);
    }

    @GetMapping("/me")
    public Map<String, Object> myDay() {
        return dashboardService.myDay(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId());
    }

    @GetMapping("/team")
    @PreAuthorize("hasAuthority('" + PermissionKeys.REPORT_VIEW + "')")
    public List<Map<String, Object>> team(@RequestParam(required = false) Instant from,
                                          @RequestParam(required = false) Instant to) {
        var range = from != null && to != null ? new DashboardService.RangeParam(from, to) : DashboardService.RangeParam.last(30);
        return dashboardService.teamPerformance(CurrentUser.require().getOrganizationId(), range);
    }

    @GetMapping("/charts")
    @PreAuthorize("hasAuthority('" + PermissionKeys.REPORT_VIEW + "')")
    public Map<String, Object> charts() {
        return dashboardService.charts(CurrentUser.require().getOrganizationId());
    }
}
