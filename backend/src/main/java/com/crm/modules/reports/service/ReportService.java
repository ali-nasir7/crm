package com.crm.modules.reports.service;

import com.crm.common.api.ApiException;
import com.crm.common.util.CsvUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;

/**
 * Reporting engine (§33). Each report returns tabular data (headers + rows); the controller can
 * serialize it as JSON or CSV. All reports are org-scoped and date-filterable.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final com.crm.modules.leads.repo.LeadRepository leads;
    private final com.crm.modules.leads.repo.LeadSourceRepository sources;
    private final com.crm.modules.activity.repo.ActivityRepository activities;
    private final com.crm.modules.calls.repo.CallRepository calls;
    private final com.crm.modules.email.repo.EmailMessageRepository emails;
    private final com.crm.modules.deals.repo.DealRepository deals;
    private final com.crm.modules.identity.repo.UserRepository users;
    private final com.crm.modules.campaigns.repo.CampaignRepository campaigns;
    private final com.crm.modules.pipeline.repo.PipelineStageRepository stages;
    private final DashboardService dashboard;

    public static final Set<String> TYPES = Set.of("lead", "activity", "call", "email", "pipeline", "revenue",
        "conversion", "team", "source", "campaign");

    public record Table(List<String> headers, List<List<Object>> rows) {}

    @Transactional(readOnly = true)
    public Table report(UUID orgId, String type, Instant from, Instant to) {
        if (!TYPES.contains(type)) throw ApiException.badRequest("Unknown report type: " + type);
        return switch (type) {
            case "lead" -> leadReport(orgId, from, to);
            case "activity" -> activityReport(orgId, from, to);
            case "call" -> callReport(orgId, from, to);
            case "email" -> emailReport(orgId, from, to);
            case "pipeline" -> pipelineReport(orgId);
            case "revenue" -> revenueReport(orgId, from, to);
            case "conversion" -> conversionReport(orgId);
            case "team" -> teamReport(orgId, from, to);
            case "source" -> sourceReport(orgId);
            case "campaign" -> campaignReport(orgId);
            default -> throw ApiException.badRequest("Unknown report type");
        };
    }

    private Table leadReport(UUID orgId, Instant from, Instant to) {
        List<String> headers = List.of("Business Name", "Contact", "Email", "Phone", "Country", "City", "Industry",
            "Status", "Score", "Stage", "Source", "Assigned To", "Created");
        List<List<Object>> rows = leads.findAll((root, cq, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId),
                cb.between(root.get("createdAt"), from, to)))
            .stream().map(l -> List.<Object>of(
                l.getBusinessName(), nz(l.contactDisplayName()), nz(l.getEmail()), nz(l.getPhone()),
                nz(l.getCountry()), nz(l.getCity()), nz(l.getIndustry()), l.getStatus().name(), l.getScore(),
                l.getStageId() != null ? stages.findById(l.getStageId()).map(s -> s.getName()).orElse("") : "",
                l.getSourceId() != null ? sources.findById(l.getSourceId()).map(s -> s.getName()).orElse("") : "",
                l.getAssignedUserId() != null ? users.findById(l.getAssignedUserId()).map(u -> u.displayName()).orElse("") : "",
                l.getCreatedAt().toString()))
            .toList();
        return new Table(headers, rows);
    }

    private Table activityReport(UUID orgId, Instant from, Instant to) {
        Map<String, Long> counts = new TreeMap<>();
        activities.findAll((root, cq, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId),
                cb.between(root.get("occurredAt"), from, to)))
            .forEach(a -> counts.merge(a.getType().name(), 1L, Long::sum));
        return new Table(List.of("Activity Type", "Count"),
            counts.entrySet().stream().map(e -> List.<Object>of(e.getKey(), e.getValue())).toList());
    }

    private Table callReport(UUID orgId, Instant from, Instant to) {
        Map<String, Long> byOutcome = new TreeMap<>();
        calls.findAll((root, cq, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId),
                cb.between(root.get("occurredAt"), from, to)))
            .forEach(c -> byOutcome.merge(c.getOutcome(), 1L, Long::sum));
        List<List<Object>> rows = new ArrayList<>(byOutcome.entrySet().stream()
            .map(e -> List.<Object>of(e.getKey(), e.getValue())).toList());
        rows.add(List.of("TOTAL", byOutcome.values().stream().mapToLong(Long::longValue).sum()));
        return new Table(List.of("Outcome", "Count"), rows);
    }

    private Table emailReport(UUID orgId, Instant from, Instant to) {
        long sent = emails.countSentBetween(orgId, from, to);
        long opened = emails.countOpenedBetween(orgId, from, to);
        long replied = emails.countRepliesBetween(orgId, from, to);
        long total = sent + opened + replied;
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Sent", sent));
        rows.add(List.of("Opened", opened, pct(opened, sent)));
        rows.add(List.of("Replied", replied, pct(replied, sent)));
        return new Table(List.of("Metric", "Count", "Rate %"), rows);
    }

    private Table pipelineReport(UUID orgId) {
        List<String> headers = List.of("Stage", "Type", "Open Deals", "Open Value");
        List<List<Object>> rows = new ArrayList<>();
        var dealsInOrg = deals.findAll((root, cq, cb) -> cb.and(
            cb.equal(root.get("organizationId"), orgId),
            cb.equal(root.get("status"), "OPEN")));
        Map<UUID, List<com.crm.modules.deals.domain.Deal>> byStage = new LinkedHashMap<>();
        dealsInOrg.forEach(d -> { if (d.getStageId() != null) byStage.computeIfAbsent(d.getStageId(), k -> new ArrayList<>()).add(d); });
        byStage.forEach((stageId, list) -> {
            String name = stages.findById(stageId).map(s -> s.getName()).orElse("Unknown");
            String type = stages.findById(stageId).map(s -> s.getType()).orElse("OPEN");
            rows.add(List.of(name, type, list.size(),
                list.stream().map(d -> d.getAmount() == null ? java.math.BigDecimal.ZERO : d.getAmount())
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)));
        });
        return new Table(headers, rows);
    }

    private Table revenueReport(UUID orgId, Instant from, Instant to) {
        BigDecimal won = deals.sumWonBetween(orgId, from, to);
        BigDecimal lost = deals.sumLostBetween(orgId, from, to);
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Won revenue", won));
        rows.add(List.of("Lost revenue", lost));
        rows.add(List.of("Open pipeline", deals.sumOpenAmount(orgId)));
        rows.add(List.of("Weighted pipeline", deals.sumWeightedAmount(orgId).setScale(2, java.math.RoundingMode.HALF_UP)));
        return new Table(List.of("Metric", "Value"), rows);
    }

    private Table conversionReport(UUID orgId) {
        Map<String, Long> byStatus = new TreeMap<>();
        leads.findAll((root, cq, cb) -> cb.equal(root.get("organizationId"), orgId))
            .forEach(l -> byStatus.merge(l.getStatus().name(), 1L, Long::sum));
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        List<List<Object>> rows = new ArrayList<>();
        byStatus.forEach((k, v) -> rows.add(List.of(k, v, pct(v, total))));
        return new Table(List.of("Status", "Count", "%"), rows);
    }

    private Table teamReport(UUID orgId, Instant from, Instant to) {
        List<Map<String, Object>> perf = dashboard.teamPerformance(orgId, new DashboardService.RangeParam(from, to));
        List<String> headers = List.of("Salesperson", "Leads", "Calls", "Connected", "Emails", "Deals Won", "Revenue");
        List<List<Object>> rows = perf.stream().map(r -> List.<Object>of(
            r.get("name"), r.get("leads"), r.get("calls"), r.get("connected"), r.get("emails"),
            r.get("dealsWon"), r.get("revenue"))).toList();
        return new Table(headers, rows);
    }

    private Table sourceReport(UUID orgId) {
        Map<UUID, long[]> bySource = new LinkedHashMap<>(); // leads, converted
        leads.findAll((root, cq, cb) -> cb.equal(root.get("organizationId"), orgId)).forEach(l -> {
            UUID key = l.getSourceId();
            long[] arr = bySource.computeIfAbsent(key, k -> new long[2]);
            arr[0]++;
            if (l.getStatus() == com.crm.modules.leads.domain.LeadStatus.CONVERTED) arr[1]++;
        });
        List<List<Object>> rows = new ArrayList<>();
        bySource.forEach((sourceId, arr) -> rows.add(List.of(
            sourceId == null ? "Unknown" : sources.findById(sourceId).map(s -> s.getName()).orElse("Unknown"),
            arr[0], arr[1], pct(arr[1], arr[0]))));
        return new Table(List.of("Source", "Leads", "Converted", "%"), rows);
    }

    private Table campaignReport(UUID orgId) {
        List<String> headers = List.of("Campaign", "Status", "Recipients", "Sent", "Opens", "Replies", "Bounces");
        List<List<Object>> rows = campaigns.findByOrganizationIdOrderByCreatedAtDesc(orgId,
                org.springframework.data.domain.PageRequest.of(0, 200)).getContent().stream()
            .map(c -> List.<Object>of(c.getName(), c.getStatus(), c.getTotalRecipients(), c.getSentCount(),
                c.getOpenCount(), c.getReplyCount(), c.getBounceCount())).toList();
        return new Table(headers, rows);
    }

    public String toCsv(Table table) {
        return CsvUtil.write(table.headers(), table.rows());
    }

    private static Object pct(long part, long total) {
        return total == 0 ? 0.0 : Math.round(part * 1000.0 / total) / 10.0;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
