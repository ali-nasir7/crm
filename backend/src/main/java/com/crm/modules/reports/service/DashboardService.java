package com.crm.modules.reports.service;

import com.crm.modules.activity.repo.ActivityRepository;
import com.crm.modules.calls.repo.CallRepository;
import com.crm.modules.deals.repo.DealRepository;
import com.crm.modules.email.repo.EmailMessageRepository;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.leads.repo.LeadRepository;
import com.crm.modules.leads.service.LeadAccessPolicy;
import com.crm.modules.meetings.repo.MeetingRepository;
import com.crm.modules.proposals.repo.ProposalRepository;
import com.crm.modules.tasks.repo.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Dashboard aggregations (executive / rep / team). All queries run against indexed org-scoped
 * filters; no table scans across tenants.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final LeadRepository leads;
    private final LeadAccessPolicy leadAccess;
    private final DealRepository deals;
    private final CallRepository calls;
    private final EmailMessageRepository emails;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final ProposalRepository proposals;
    private final ActivityRepository activities;
    private final MeetingRepository meetings;
    private final com.crm.modules.leads.repo.LeadSourceRepository sourceRepository;
    private final com.crm.modules.pipeline.repo.PipelineStageRepository stageRepository;

    public record RangeParam(Instant from, Instant to) {
        public static RangeParam last(int days) {
            return new RangeParam(Instant.now().minus(Duration.ofDays(days)), Instant.now());
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> executive(UUID orgId, RangeParam range) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalLeads", leads.count((root, cq, cb) -> cb.equal(root.get("organizationId"), orgId)));
        m.put("newLeads", leads.count((root, cq, cb) -> cb.and(
            cb.equal(root.get("organizationId"), orgId),
            cb.equal(root.get("status"), com.crm.modules.leads.domain.LeadStatus.NEW))));
        m.put("qualifiedLeads", leads.count((root, cq, cb) -> cb.and(
            cb.equal(root.get("organizationId"), orgId),
            cb.equal(root.get("status"), com.crm.modules.leads.domain.LeadStatus.QUALIFIED))));
        m.put("interestedLeads", leads.count((root, cq, cb) -> cb.and(
            cb.equal(root.get("organizationId"), orgId),
            cb.equal(root.get("status"), com.crm.modules.leads.domain.LeadStatus.INTERESTED))));
        m.put("convertedLeads", leads.count((root, cq, cb) -> cb.and(
            cb.equal(root.get("organizationId"), orgId),
            cb.equal(root.get("status"), com.crm.modules.leads.domain.LeadStatus.CONVERTED))));

        long open = deals.countByOrganizationIdAndStatus(orgId, "OPEN");
        long won = deals.countByOrganizationIdAndStatus(orgId, "WON");
        long lost = deals.countByOrganizationIdAndStatus(orgId, "LOST");
        m.put("openDeals", open);
        m.put("wonDeals", won);
        m.put("lostDeals", lost);
        m.put("pipelineValue", deals.sumOpenAmount(orgId));
        m.put("weightedPipeline", deals.sumWeightedAmount(orgId).setScale(2, RoundingMode.HALF_UP));
        m.put("wonRevenue", deals.sumAmountByStatus(orgId, "WON"));
        m.put("lostRevenue", deals.sumAmountByStatus(orgId, "LOST"));
        long closed = won + lost;
        m.put("winRate", closed == 0 ? 0.0 : Math.round(won * 1000.0 / closed) / 10.0);

        Instant from = range.from(), to = range.to();
        long sent = emails.countSentBetween(orgId, from, to);
        long replies = emails.countRepliesBetween(orgId, from, to);
        long opened = emails.countOpenedBetween(orgId, from, to);
        m.put("emailsSent", sent);
        m.put("emailReplies", replies);
        m.put("replyRate", sent == 0 ? 0.0 : Math.round(replies * 1000.0 / sent) / 10.0);
        m.put("openRate", sent == 0 ? 0.0 : Math.round(opened * 1000.0 / sent) / 10.0);

        // 30d/7d window metrics consumed by the executive dashboard cards
        Instant last30 = Instant.now().minus(Duration.ofDays(30));
        m.put("newLeads30d", leads.count((root, cq, cb) -> cb.and(
            cb.equal(root.get("organizationId"), orgId),
            cb.greaterThanOrEqualTo(root.get("createdAt"), last30))));
        m.put("converted30d", leads.count((root, cq, cb) -> cb.and(
            cb.equal(root.get("organizationId"), orgId),
            cb.equal(root.get("status"), com.crm.modules.leads.domain.LeadStatus.CONVERTED),
            cb.greaterThanOrEqualTo(root.get("createdAt"), last30))));
        m.put("openPipelineValue", deals.sumOpenAmount(orgId));
        m.put("calls7d", calls.countByOrganizationIdAndOccurredAtBetween(
            orgId, Instant.now().minus(Duration.ofDays(7)), Instant.now()));
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> myDay(UUID orgId, UUID userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        Instant now = Instant.now();
        Instant dayStart = java.time.LocalDate.now(java.time.ZoneOffset.UTC).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Instant dayEnd = dayStart.plus(Duration.ofDays(1));
        m.put("myLeads", leads.count((root, cq, cb) -> cb.and(
            cb.equal(root.get("organizationId"), orgId),
            cb.equal(root.get("assignedUserId"), userId))));
        m.put("tasksToday", tasks.countByOrganizationIdAndAssignedUserIdAndStatusAndDueAtBetween(orgId, userId, "OPEN", dayStart, dayEnd));
        m.put("tasksOverdue", tasks.countByOrganizationIdAndAssignedUserIdAndStatusAndDueAtBefore(orgId, userId, "OPEN", now));
        m.put("callsToday", calls.countByOrganizationIdAndUserIdAndOccurredAtBetween(orgId, userId, dayStart, dayEnd));

        var user = users.findById(userId).orElse(null);
        Map<String, Object> targets = new LinkedHashMap<>();
        int callTarget = user != null && user.getDailyTargets() != null
            ? user.getDailyTargets().getOrDefault("calls", 20) : 20;
        int emailTarget = user != null && user.getDailyTargets() != null
            ? user.getDailyTargets().getOrDefault("emails", 50) : 50;
        int meetingTarget = user != null && user.getDailyTargets() != null
            ? user.getDailyTargets().getOrDefault("meetings", 5) : 5;
        long callsToday = calls.countByOrganizationIdAndUserIdAndOccurredAtBetween(orgId, userId, dayStart, dayEnd);
        // per-user emails today
        long myEmailsToday = emails.count((root, cq, cb) -> cb.and(
            cb.equal(root.get("organizationId"), orgId),
            cb.equal(root.get("userId"), userId),
            cb.between(root.get("createdAt"), dayStart, dayEnd)));
        long myMeetingsToday = meetings.count((root, cq, cb) -> cb.and(
            cb.equal(root.get("organizationId"), orgId),
            cb.equal(root.get("ownerId"), userId),
            cb.between(root.get("startAt"), dayStart, dayEnd)));
        targets.put("calls", Map.of("target", callTarget, "done", callsToday));
        targets.put("emails", Map.of("target", emailTarget, "done", myEmailsToday));
        targets.put("meetings", Map.of("target", meetingTarget, "done", myMeetingsToday));
        m.put("targets", targets);

        m.put("followUpsDue", leads.count((root, cq, cb) -> cb.and(
            cb.equal(root.get("organizationId"), orgId),
            cb.equal(root.get("assignedUserId"), userId),
            cb.isNotNull(root.get("nextFollowUpAt")),
            cb.lessThanOrEqualTo(root.get("nextFollowUpAt"), now))));
        return m;
    }

    /** Manager view: per-user performance rows within a date range. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> teamPerformance(UUID orgId, RangeParam range) {
        Map<UUID, Map<String, Object>> byUser = new LinkedHashMap<>();
        users.findByOrganizationId(orgId).forEach(u -> byUser.put(u.getId(), new java.util.LinkedHashMap<>(Map.of(
            "userId", u.getId(), "name", u.displayName(), "leads", 0, "calls", 0, "connected", 0, "emails", 0,
            "meetings", 0, "dealsWon", 0, "revenue", BigDecimal.ZERO))));

        leads.findAll((root, cq, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId),
                cb.between(root.get("createdAt"), range.from(), range.to())))
            .forEach(l -> {
                if (l.getAssignedUserId() != null && byUser.containsKey(l.getAssignedUserId())) {
                    var row = byUser.get(l.getAssignedUserId());
                    row.put("leads", (int) row.get("leads") + 1);
                }
            });

        for (Object[] row : calls.countByUserBetween(orgId, range.from(), range.to())) {
            UUID userId = (UUID) row[0];
            if (byUser.containsKey(userId)) byUser.get(userId).put("calls", ((Number) row[1]).intValue());
        }
        for (Object[] row : calls.countConnectedByUserBetween(orgId, range.from(), range.to())) {
            UUID userId = (UUID) row[0];
            if (byUser.containsKey(userId)) byUser.get(userId).put("connected", ((Number) row[1]).intValue());
        }
        for (Object[] row : emails.countByUserBetween(orgId, range.from(), range.to())) {
            UUID userId = (UUID) row[0];
            if (byUser.containsKey(userId)) byUser.get(userId).put("emails", ((Number) row[1]).intValue());
        }
        for (Object[] row : deals.ownerStats(orgId, range.from(), range.to())) {
            UUID userId = (UUID) row[0];
            if (byUser.containsKey(userId)) {
                byUser.get(userId).put("dealsWon", ((Number) row[1]).intValue());
                byUser.get(userId).put("revenue", row[2]);
            }
        }
        return new ArrayList<>(byUser.values());
    }

    /**
     * Charts data in the contract the UI renders (arrays of row objects, never raw maps):
     *   leadsBySource   -> [{name, count}]  (source NAMES resolved, "Unknown" for null)
     *   leadsPerDay     -> [{day, count}]   (last 30 days, DB-grouped)
     *   pipelineByStage -> [{stage, count, value}] (open deals per stage)
     *   leadsByStatus   -> [{status, count}]
     *   leadsByIndustry -> {industry: count} (legacy map, not consumed by the UI)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> charts(UUID orgId) {
        Map<String, Object> m = new LinkedHashMap<>();

        // single pass over org leads for bySource + byStatus + byIndustry
        Map<UUID, Long> bySourceId = new LinkedHashMap<>();
        Map<String, Long> byStatus = new java.util.TreeMap<>();
        Map<String, Long> byIndustry = new java.util.TreeMap<>();
        long unknownSource = 0;
        for (var l : leads.findAll((root, cq, cb) -> cb.equal(root.get("organizationId"), orgId))) {
            if (l.getSourceId() == null) unknownSource++; else bySourceId.merge(l.getSourceId(), 1L, Long::sum);
            byStatus.merge(l.getStatus().name(), 1L, Long::sum);
            byIndustry.merge(l.getIndustry() == null || l.getIndustry().isBlank() ? "Unknown" : l.getIndustry(), 1L, Long::sum);
        }

        Map<String, Long> sourceCounts = new LinkedHashMap<>();
        if (!bySourceId.isEmpty()) {
            sourceRepository.findAllById(bySourceId.keySet())
                .forEach(s -> sourceCounts.put(s.getName(), bySourceId.get(s.getId())));
        }
        if (unknownSource > 0) sourceCounts.merge("Unknown", unknownSource, Long::sum);
        m.put("leadsBySource", sourceCounts.entrySet().stream()
            .map(e -> Map.<String, Object>of("name", e.getKey(), "count", e.getValue())).toList());

        m.put("leadsByIndustry", byIndustry);

        m.put("leadsPerDay", leads.countPerDay(orgId, Instant.now().minus(Duration.ofDays(30))).stream()
            .map(row -> Map.<String, Object>of("day", String.valueOf(row[0]), "count", ((Number) row[1]).longValue()))
            .toList());

        Map<UUID, Object[]> stageRows = new LinkedHashMap<>();
        for (Object[] row : deals.openByStage(orgId)) stageRows.put((UUID) row[0], row);
        List<Map<String, Object>> byStage = new ArrayList<>();
        if (!stageRows.isEmpty()) {
            stageRepository.findAllById(stageRows.keySet()).forEach(st -> {
                Object[] row = stageRows.get(st.getId());
                byStage.add(Map.of("stage", st.getName(),
                    "count", ((Number) row[1]).longValue(), "value", row[2]));
            });
        }
        m.put("pipelineByStage", byStage);

        m.put("leadsByStatus", byStatus.entrySet().stream()
            .map(e -> Map.<String, Object>of("status", e.getKey(), "count", e.getValue())).toList());
        return m;
    }
}
