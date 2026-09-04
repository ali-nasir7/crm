package com.crm.modules.automation.service;

import com.crm.modules.activity.domain.ActivityType;
import com.crm.modules.activity.service.ActivityService;
import com.crm.modules.automation.domain.AutomationRule;
import com.crm.modules.automation.domain.AutomationRun;
import com.crm.modules.automation.repo.AutomationRuleRepository;
import com.crm.modules.automation.repo.AutomationRunRepository;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.leads.domain.Lead;
import com.crm.modules.leads.repo.LeadRepository;
import com.crm.modules.leads.repo.TagRepository;
import com.crm.modules.notifications.service.NotificationService;
import com.crm.modules.pipeline.repo.PipelineStageRepository;
import com.crm.modules.tasks.domain.Task;
import com.crm.modules.tasks.repo.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rules engine: fires typed events from domain services, evaluates active rules and executes
 * their actions. Every execution is logged to automation_runs (auditable automation).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationEngine {

    private final AutomationRuleRepository rules;
    private final AutomationRunRepository runs;
    private final LeadRepository leads;
    private final TagRepository tags;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final NotificationService notifications;
    private final PipelineStageRepository stages;
    private final ActivityService activities;

    // ---------- event entry points ----------

    public void leadCreated(UUID orgId, UUID leadId) {
        evaluate(orgId, AutomationRule.Trigger.LEAD_CREATED, leadId, Map.of());
    }

    public void stageChanged(UUID orgId, UUID leadId, UUID fromStage, UUID toStage) {
        String toName = stages.findById(toStage).map(s -> s.getName()).orElse("");
        evaluate(orgId, AutomationRule.Trigger.LEAD_STAGE_CHANGED, leadId, Map.of("toStageName", toName));
    }

    public void callLogged(UUID orgId, UUID leadId, String outcome) {
        evaluate(orgId, AutomationRule.Trigger.CALL_LOGGED, leadId, Map.of("outcome", outcome));
    }

    // ---------- evaluation ----------

    private void evaluate(UUID orgId, AutomationRule.Trigger trigger, UUID leadId, Map<String, Object> context) {
        List<AutomationRule> active = rules.findByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
            .filter(AutomationRule::isActive)
            .filter(r -> r.getTrigger() == trigger)
            .toList();
        if (active.isEmpty()) return;
        Lead lead = leadId == null ? null : leads.findById(leadId).orElse(null);
        for (AutomationRule rule : active) {
            try {
                if (conditionsMet(rule, lead, context)) {
                    execute(orgId, rule, lead);
                }
            } catch (Exception e) {
                log.warn("Automation rule {} failed: {}", rule.getName(), e.getMessage());
                record(orgId, rule, leadId, "FAILED", e.getMessage());
            }
        }
    }

    private boolean conditionsMet(AutomationRule rule, Lead lead, Map<String, Object> context) {
        Map<String, Object> conditions = rule.getConditions();
        if (conditions == null || conditions.isEmpty()) return true;
        if (conditions.containsKey("toStageName")) {
            if (!String.valueOf(conditions.get("toStageName")).equalsIgnoreCase(String.valueOf(context.get("toStageName")))) return false;
        }
        if (conditions.containsKey("outcome")) {
            if (!String.valueOf(conditions.get("outcome")).equalsIgnoreCase(String.valueOf(context.get("outcome")))) return false;
        }
        if (conditions.containsKey("status")) {
            if (lead == null || !String.valueOf(conditions.get("status")).equalsIgnoreCase(lead.getStatus().name())) return false;
        }
        return true;
    }

    private void execute(UUID orgId, AutomationRule rule, Lead lead) {
        Map<String, Object> config = rule.getActionConfig() == null ? Map.of() : rule.getActionConfig();
        switch (rule.getAction()) {
            case CREATE_TASK -> {
                Task t = new Task();
                t.setOrganizationId(orgId);
                t.setTitle(String.valueOf(config.getOrDefault("title", "Automated follow-up")));
                t.setTaskType("FOLLOW_UP");
                t.setLeadId(lead != null ? lead.getId() : null);
                t.setAssignedUserId(lead != null && lead.getAssignedUserId() != null ? lead.getAssignedUserId() : rule.getCreatedBy());
                int dueInDays = ((Number) config.getOrDefault("dueInDays", 2)).intValue();
                t.setDueAt(Instant.now().plus(Duration.ofDays(dueInDays)));
                t.setPriority(String.valueOf(config.getOrDefault("priority", "MEDIUM")).toUpperCase());
                tasks.save(t);
                if (lead != null) {
                    activities.record(orgId, ActivityType.TASK_CREATED, lead.getId(),
                        "Automated task: " + t.getTitle(), null, Map.of("automation", rule.getName()), null);
                }
                record(orgId, rule, lead == null ? null : lead.getId(), "EXECUTED", "Task created: " + t.getTitle());
            }
            case ADD_TAG -> {
                if (lead == null) return;
                String tagName = String.valueOf(config.getOrDefault("tag", "Automated"));
                var tag = tags.findByOrganizationIdOrderByNameAsc(orgId).stream()
                    .filter(t2 -> t2.getName().equalsIgnoreCase(tagName)).findFirst().orElseGet(() -> {
                        var t2 = new com.crm.modules.leads.domain.Tag();
                        t2.setOrganizationId(orgId);
                        t2.setName(tagName);
                        return tags.save(t2);
                    });
                lead.getTags().add(tag);
                leads.save(lead);
                record(orgId, rule, lead.getId(), "EXECUTED", "Tag added: " + tagName);
            }
            case NOTIFY -> {
                UUID target = lead != null && lead.getAssignedUserId() != null ? lead.getAssignedUserId() : rule.getCreatedBy();
                if (target != null) {
                    notifications.notify(orgId, target, "AUTOMATION",
                        String.valueOf(config.getOrDefault("message", "Automation: " + rule.getName())), null, "LEAD",
                        lead == null ? null : lead.getId());
                }
                record(orgId, rule, lead == null ? null : lead.getId(), "EXECUTED", "Notification sent");
            }
            case CHANGE_STAGE -> {
                if (lead == null) return;
                String stageName = String.valueOf(config.getOrDefault("stageName", ""));
                stages.findByPipelineIdOrderByPositionAsc(lead.getPipelineId()).stream()
                    .filter(s -> s.getName().equalsIgnoreCase(stageName)).findFirst()
                    .ifPresent(s -> { lead.setStageId(s.getId()); leads.save(lead); });
                record(orgId, rule, lead.getId(), "EXECUTED", "Stage moved: " + stageName);
            }
            case SEND_EMAIL -> {
                // TODO / Integration Required: connect to campaign sender with owner's verified account.
                record(orgId, rule, lead == null ? null : lead.getId(), "SKIPPED",
                    "SEND_EMAIL requires a connected sending account (TODO / Integration Required)");
            }
        }
        rule.setRunCount(rule.getRunCount() + 1);
        rules.save(rule);
    }

    private void record(UUID orgId, AutomationRule rule, UUID leadId, String status, String detail) {
        AutomationRun run = new AutomationRun();
        run.setRuleId(rule.getId());
        run.setOrganizationId(orgId);
        run.setLeadId(leadId);
        run.setStatus(status);
        run.setDetail(detail);
        runs.save(run);
    }
}
