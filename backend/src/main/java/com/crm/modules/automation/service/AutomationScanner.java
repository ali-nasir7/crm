package com.crm.modules.automation.service;

import com.crm.modules.automation.repo.AutomationRuleRepository;
import com.crm.modules.identity.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic scanner for time-based triggers. NO_REPLY_AFTER and TASK_OVERDUE rules are surfaced as
 * notifications to lead owners. Delivery of email campaigns is handled by CampaignWorker.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationScanner {

    private final AutomationRuleRepository rules;
    private final com.crm.modules.tasks.service.TaskService taskService;
    private final com.crm.modules.leads.repo.LeadRepository leadRepository;
    private final com.crm.modules.email.repo.EmailMessageRepository emailMessages;
    private final com.crm.modules.notifications.service.NotificationService notifications;
    private final UserRepository users;

    // fired hourly; cheap index-backed scans per org
    @Scheduled(fixedDelayString = "3600000", initialDelayString = "60000")
    public void scan() {
        try {
            users.findAll().stream().map(u -> u.getOrganizationId()).distinct().forEach(this::scanOrg);
        } catch (Exception e) {
            log.error("Automation scan failed", e);
        }
    }

    private void scanOrg(java.util.UUID orgId) {
        var active = rules.findByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
            .filter(com.crm.modules.automation.domain.AutomationRule::isActive).toList();

        var noReply = active.stream().filter(r -> r.getTrigger() == com.crm.modules.automation.domain.AutomationRule.Trigger.NO_REPLY_AFTER).toList();
        if (!noReply.isEmpty()) {
            var recentLeads = leadRepository.findAll((root, cq, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId),
                cb.isNotNull(root.get("lastContactedAt")),
                cb.lessThan(root.get("lastContactedAt"), java.time.Instant.now().minus(java.time.Duration.ofDays(1)))),
                org.springframework.data.domain.PageRequest.of(0, 100)).getContent();
            for (var lead : recentLeads) {
                boolean replied = emailMessages.findAll((root, cq, cb) -> cb.and(
                        cb.equal(root.get("organizationId"), orgId),
                        cb.equal(root.get("leadId"), lead.getId()),
                        cb.equal(root.get("direction"), com.crm.modules.email.domain.EmailMessage.Direction.INBOUND)),
                    org.springframework.data.domain.PageRequest.of(0, 1)).isEmpty();
                if (!replied && lead.getAssignedUserId() != null) {
                    for (var rule : noReply) {
                        int days = rule.getConditions() != null && rule.getConditions().containsKey("days")
                            ? ((Number) rule.getConditions().get("days")).intValue() : 3;
                        if (lead.getLastContactedAt().isBefore(java.time.Instant.now().minus(java.time.Duration.ofDays(days)))) {
                            notifications.notify(orgId, lead.getAssignedUserId(), "NO_REPLY",
                                "No reply from " + lead.getBusinessName() + " after " + days + " days",
                                "Consider a follow-up task", "LEAD", lead.getId());
                        }
                    }
                }
            }
        }

        var overdue = active.stream().filter(r -> r.getTrigger() == com.crm.modules.automation.domain.AutomationRule.Trigger.TASK_OVERDUE).toList();
        if (!overdue.isEmpty()) {
            for (var task : taskService.overdue(orgId, 100)) {
                notifications.notify(orgId, task.getAssignedUserId(), "TASK_OVERDUE",
                    "Overdue task: " + task.getTitle(), "Due " + task.getDueAt(), "TASK", task.getId());
            }
        }
    }
}
