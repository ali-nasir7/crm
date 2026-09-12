package com.crm.modules.reports.web;

import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.reports.service.ReportService;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/{type}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.REPORT_VIEW + "')")
    public ResponseEntity<Object> report(@PathVariable String type,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                         @RequestParam(defaultValue = "json") String format) {
        var range = from != null && to != null ? new Instant[]{from, to}
            : new Instant[]{Instant.now().minus(java.time.Duration.ofDays(30)), Instant.now()};
        var table = reportService.report(CurrentUser.require().getOrganizationId(), type, range[0], range[1]);
        if ("csv".equalsIgnoreCase(format)) {
            String csv = reportService.toCsv(table);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + type + "-report.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
        }
        return ResponseEntity.ok().body(Map.of("type", type, "headers", table.headers(), "rows", table.rows()));
    }
}
