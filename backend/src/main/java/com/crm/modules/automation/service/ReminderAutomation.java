package com.crm.modules.automation.service;

import com.crm.common.api.ApiException;
import com.crm.modules.activity.domain.ActivityType;
import com.crm.modules.activity.service.ActivityService;
import com.crm.modules.audit.service.AuditService;
import com.crm.modules.email.domain.EmailAccount;
import com.crm.modules.email.domain.EmailMessage;
import com.crm.modules.email.repo.EmailAccountRepository;
import com.crm.modules.email.repo.EmailMessageRepository;
import com.crm.modules.email.service.EmailDispatchService;
import com.crm.modules.identity.domain.User;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.leads.domain.Lead;
import com.crm.modules.leads.domain.LeadStatus;
import com.crm.modules.leads.repo.LeadRepository;
import com.crm.modules.notifications.service.NotificationService;
import com.crm.modules.tasks.domain.Task;
import com.crm.modules.tasks.repo.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Brief #16-18: when a lead becomes HOT (score >= 50 in the existing scoring model - HOT
 * starts at 50, VERY_HOT at 75) a 3-day no-reply reminder is scheduled for the lead's
 * RESPONSIBLE PERSON (assigned user per the existing ownership model - never "the admin").
 *
 * Idempotency: the reminder IS an OPEN task with task_type REMINDER - one per lead max.
 * The scheduled sweep only emails when the reminder falls due AND the lead still has no
 * inbound reply AND the lead is still open AND the responsible user is still active.
 * Missing sender configuration leaves the reminder OPEN (retried next sweep) - it is
 * never silently dropped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderAutomation {

    public static final String TASK_TYPE = "REMINDER";
    private static final int SCORE_THRESHOLD = 50;
    private static final int REMINDER_AFTER_DAYS = 3;

    private final TaskRepository tasks;
    private final LeadRepository leads;
    private final UserRepository users;
    private final EmailMessageRepository emailMessages;
    private final EmailAccountRepository accounts;
    private final EmailDispatchService dispatch;
    private final NotificationService notifications;
    private final ActivityService activities;
    private final AuditService audit;

    // ---------- trigger point (called from LeadService / ImportService) ----------

    public void ensureScheduled(UUID orgId, Lead lead) {
        try {
            if (lead == null || lead.getId() == null || lead.getDeletedAt() != null) return;
            if (lead.getScore() < SCORE_THRESHOLD) return;
            if (lead.getStatus() == LeadStatus.CONVERTED || lead.getStatus() == LeadStatus.WON || lead.getStatus() == LeadStatus.UNQUALIFIED) return;
            UUID owner = lead.getAssignedUserId();
            if (owner == null) return; // no responsible person -> cannot route the reminder
            if (alreadyReplied(orgId, lead.getId())) return;
            if (tasks.existsByOrganizationIdAndLeadIdAndTaskTypeAndStatus(orgId, lead.getId(), TASK_TYPE, "OPEN")) return;

            Task t = new Task();
            t.setOrganizationId(orgId);
            t.setLeadId(lead.getId());
            t.setTaskType(TASK_TYPE);
            t.setTitle("3-day no-reply reminder: " + lead.getBusinessName());
            t.setDescription("Lead reached HOT score (" + lead.getScore() + "). If there is still no reply after "
                + REMINDER_AFTER_DAYS + " days, email the responsible user automatically.");
            t.setAssignedUserId(owner);
            t.setDueAt(Instant.now().plus(Duration.ofDays(REMINDER_AFTER_DAYS)));
            t.setPriority("MEDIUM");
            tasks.save(t);
            audit.log("REMINDER_SCHEDULED", "LEAD", lead.getId(), lead.getBusinessName(), null,
                Map.of("score", lead.getScore(), "dueAt", t.getDueAt().toString(), "assignedTo", owner.toString()));
        } catch (Exception e) {
            log.warn("Reminder scheduling failed for lead {}: {}", lead == null ? null : lead.getId(), e.getMessage());
        }
    }

    // ---------- scheduled sender ----------

    /** Every 10 minutes: process due reminders (small capped batch; failures never break the sweep). */
    @Scheduled(fixedDelayString = "600000", initialDelayString = "120000")
    public void sweep() {
        try {
            List<Task> due = tasks.findByTaskTypeAndStatusAndDueAtLessThanEqual(TASK_TYPE, "OPEN", Instant.now(), PageRequest.of(0, 100));
            for (Task t : due) {
                try {
                    process(t);
                } catch (Exception e) {
                    log.warn("Reminder task {} failed: {}", t.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Reminder sweep failed", e);
        }
    }

    private void process(Task t) {
        UUID orgId = t.getOrganizationId();
        if (t.getLeadId() == null) { complete(t, "reminder had no lead attached"); return; }
        Lead lead = leads.findById(t.getLeadId()).orElse(null);
        if (lead == null || lead.getDeletedAt() != null) { complete(t, "lead deleted"); return; }
        if (lead.getStatus() == LeadStatus.CONVERTED) { complete(t, "lead converted"); return; }
        if (lead.getStatus() == LeadStatus.WON) { complete(t, "lead won - flow completed"); return; }
        if (lead.getStatus() == LeadStatus.UNQUALIFIED) { complete(t, "lead marked lost/unqualified"); return; }
        if (alreadyReplied(orgId, lead.getId())) { complete(t, "lead replied - reminder not needed"); return; }

        if (t.getAssignedUserId() == null) { complete(t, "no responsible user on the lead"); return; }
        User owner = users.findById(t.getAssignedUserId())
            .filter(u -> u.getOrganizationId().equals(orgId))
            .filter(u -> u.getStatus() == null || "ACTIVE".equals(u.getStatus().name()))
            .orElse(null);
        if (owner == null) { complete(t, "responsible user deleted or inactive"); return; }

        EmailAccount account = accounts
            .findFirstByOrganizationIdAndProviderAndStatusOrderByCreatedAtAsc(orgId, EmailAccount.Provider.SMTP, "VERIFIED")
            .orElse(null);
        if (account == null) {
            // Keep OPEN - retried next sweep once a sender is configured. Surfaced honestly.
            log.warn("Reminder for lead {} is due but org {} has no VERIFIED SMTP sender configured; will retry",
                lead.getId(), orgId);
            return;
        }

        String business = lead.getBusinessName() == null || lead.getBusinessName().isBlank()
            ? "our team" : lead.getBusinessName();
        String subject = "[" + business + "] Reminder";
        String bodyText = "Hello " + (owner.displayName() == null || owner.displayName().isBlank() ? owner.getEmail() : owner.displayName()) + ",\n\n"
            + "The lead \"" + business + "\" has not received a reply for " + REMINDER_AFTER_DAYS
            + " days and needs a follow-up. Please reach out or update the lead in the CRM.\n\n"
            + "Reminder form Ali Nasir , the great software Engineer";
        String bodyHtml = "<p>Hello " + esc(owner.displayName() == null || owner.displayName().isBlank() ? owner.getEmail() : owner.displayName()) + ",</p>"
            + "<p>The lead <b>" + esc(business) + "</b> has not received a reply for <b>" + REMINDER_AFTER_DAYS
            + " days</b> and needs a follow-up. Please reach out or update the lead in the CRM.</p>"
            + "<p style='color:#94a3b8;font-size:12px'>Reminder form Ali Nasir , the great software Engineer</p>";
        try {
            dispatch.dispatch(orgId, account.getId(), List.of(owner.getEmail()), List.of(), subject, bodyHtml, bodyText);
        } catch (ApiException e) {
            log.warn("Reminder email for lead {} could not be sent: {}", lead.getId(), e.getMessage());
            return; // stays OPEN; retried next sweep
        }
        complete(t, "reminder emailed to " + owner.getEmail() + " via " + account.getEmail());
        activities.record(orgId, ActivityType.SYSTEM, lead.getId(), "No-reply reminder sent",
            "To: " + owner.getEmail(), Map.of("automation", "no-reply-reminder", "score", lead.getScore()), null);
        notifications.notify(orgId, owner.getId(), "REMINDER_SENT",
            "Reminder sent for " + business, "The lead had no reply for " + REMINDER_AFTER_DAYS + " days", "LEAD", lead.getId());
        audit.log("REMINDER_SENT", "LEAD", lead.getId(), lead.getBusinessName(), null,
            Map.of("to", owner.getEmail(), "sender", account.getEmail()));
    }

    private void complete(Task t, String note) {
        t.setStatus("COMPLETED");
        t.setCompletedAt(Instant.now());
        t.setCompletionNote(note);
        tasks.save(t);
    }

    /** "Replied" = any inbound email on the lead (same definition the existing no-reply scanner uses). */
    private boolean alreadyReplied(UUID orgId, UUID leadId) {
        return !emailMessages.findAll((root, cq, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId),
                cb.equal(root.get("leadId"), leadId),
                cb.equal(root.get("direction"), EmailMessage.Direction.INBOUND)),
            PageRequest.of(0, 1)).isEmpty();
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
