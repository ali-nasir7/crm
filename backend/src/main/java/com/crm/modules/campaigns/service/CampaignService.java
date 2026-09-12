package com.crm.modules.campaigns.service;

import com.crm.common.api.ApiException;
import com.crm.common.api.PageResponse;
import com.crm.modules.activity.domain.ActivityType;
import com.crm.modules.activity.service.ActivityService;
import com.crm.modules.campaigns.domain.Campaign;
import com.crm.modules.campaigns.domain.CampaignRecipient;
import com.crm.modules.campaigns.domain.CampaignStep;
import com.crm.modules.campaigns.repo.CampaignRecipientRepository;
import com.crm.modules.campaigns.repo.CampaignRepository;
import com.crm.modules.campaigns.repo.CampaignStepRepository;
import com.crm.modules.email.domain.EmailAccount;
import com.crm.modules.email.domain.EmailMessage;
import com.crm.modules.email.repo.EmailAccountRepository;
import com.crm.modules.email.repo.EmailMessageRepository;
import com.crm.modules.email.repo.EmailTemplateRepository;
import com.crm.modules.email.service.EmailDispatchService;
import com.crm.modules.email.service.EmailService;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.leads.domain.Lead;
import com.crm.modules.leads.repo.LeadRepository;
import com.crm.modules.organization.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Campaign engine: recipients enter the sequence, the worker advances each recipient one step at a
 * time, honoring the step delay, org sending window, suppression list and account daily limits.
 * Every send is authorized by explicit CAMPAIGN_SEND permission + user action (start) — no auto-fire.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaigns;
    private final CampaignStepRepository steps;
    private final CampaignRecipientRepository recipients;
    private final EmailAccountRepository accounts;
    private final EmailTemplateRepository templates;
    private final EmailMessageRepository messages;
    private final EmailDispatchService dispatch;
    private final LeadRepository leads;
    private final UserRepository users;
    private final ActivityService activities;
    private final SettingsService settings;

    public record CampaignItem(UUID id, String name, String description, UUID accountId, String accountEmail,
                               String status, Instant scheduledAt, int totalRecipients, int sentCount,
                               int openCount, int replyCount, int bounceCount, int unsubscribeCount,
                               List<StepItem> steps, Instant createdAt) {}

    public record StepItem(UUID id, int position, UUID templateId, int delayDays) {}

    public record RecipientItem(UUID id, UUID leadId, String email, String status, Integer currentStep,
                                Instant nextSendAt, String errorMessage) {}

    public record CreateCampaignRequest(String name, String description, UUID accountId,
                                        List<StepRequest> steps) {}
    public record StepRequest(UUID templateId, int delayDays) {}
    public record AddRecipientsRequest(List<UUID> leadIds) {}

    // ---------- CRUD ----------

    @Transactional(readOnly = true)
    public PageResponse<CampaignItem> list(UUID orgId, int page, int size) {
        var result = campaigns.findByOrganizationIdOrderByCreatedAtDesc(orgId, PageRequest.of(page, Math.min(size, 100)));
        return PageResponse.of(result.map(this::toItem));
    }

    @Transactional(readOnly = true)
    public CampaignItem get(UUID orgId, UUID id) {
        return toItem(find(orgId, id));
    }

    @Transactional
    public CampaignItem create(UUID orgId, UUID userId, CreateCampaignRequest req) {
        if (req.steps() == null || req.steps().isEmpty()) throw ApiException.badRequest("A campaign needs at least one step");
        Campaign c = new Campaign();
        c.setOrganizationId(orgId);
        c.setName(req.name() == null ? "Untitled campaign" : req.name().trim());
        c.setDescription(req.description());
        c.setAccountId(req.accountId());
        campaigns.save(c);

        int pos = 0;
        for (StepRequest sr : req.steps()) {
            if (templates.findById(sr.templateId()).filter(t -> t.getOrganizationId().equals(orgId)).isEmpty()) {
                throw ApiException.badRequest("Step template not found in organization");
            }
            CampaignStep s = new CampaignStep();
            s.setCampaignId(c.getId());
            s.setPosition(pos++);
            s.setTemplateId(sr.templateId());
            s.setDelayDays(Math.max(0, sr.delayDays()));
            steps.save(s);
        }
        return toItem(c);
    }

    @Transactional
    public CampaignItem update(UUID orgId, UUID id, CreateCampaignRequest req) {
        Campaign c = find(orgId, id);
        if (!"DRAFT".equals(c.getStatus()) && !"PAUSED".equals(c.getStatus())) {
            throw ApiException.business("Only draft or paused campaigns can be edited");
        }
        if (req.name() != null) c.setName(req.name().trim());
        if (req.description() != null) c.setDescription(req.description());
        if (req.accountId() != null) c.setAccountId(req.accountId());
        campaigns.save(c);
        if (req.steps() != null && !req.steps().isEmpty()) {
            steps.deleteByCampaignId(c.getId());
            int pos = 0;
            for (StepRequest sr : req.steps()) {
                CampaignStep s = new CampaignStep();
                s.setCampaignId(c.getId());
                s.setPosition(pos++);
                s.setTemplateId(sr.templateId());
                s.setDelayDays(Math.max(0, sr.delayDays()));
                steps.save(s);
            }
        }
        return toItem(c);
    }

    @Transactional
    public void delete(UUID orgId, UUID id) {
        Campaign c = find(orgId, id);
        if ("RUNNING".equals(c.getStatus())) throw ApiException.business("Pause the campaign before deleting it");
        c.setDeletedAt(Instant.now());
        campaigns.save(c);
    }

    @Transactional
    public int addRecipients(UUID orgId, UUID id, List<UUID> leadIds) {
        Campaign c = find(orgId, id);
        int added = 0;
        for (UUID leadId : leadIds) {
            Lead lead = leads.findInOrg(orgId, leadId).orElse(null);
            if (lead == null) continue;
            if (lead.getEmail() == null || lead.getEmail().isBlank()) continue; // no channel → skip
            if (recipients.findByCampaignIdAndLeadId(c.getId(), leadId).isPresent()) continue; // already enrolled
            if (dispatch.isSuppressed(orgId, lead.getEmail())) {
                CampaignRecipient r = new CampaignRecipient();
                r.setCampaignId(c.getId());
                r.setOrganizationId(orgId);
                r.setLeadId(leadId);
                r.setEmail(lead.getEmail());
                r.setStatus("UNSUBSCRIBED");
                recipients.save(r);
                continue;
            }
            CampaignRecipient r = new CampaignRecipient();
            r.setCampaignId(c.getId());
            r.setOrganizationId(orgId);
            r.setLeadId(leadId);
            r.setEmail(lead.getEmail());
            r.setStatus("PENDING");
            recipients.save(r);
            added++;
        }
        c.setTotalRecipients((int) recipients.findByCampaignIdOrderByCreatedAtAsc(c.getId(), PageRequest.of(0, 1)).getTotalElements());
        campaigns.save(c);
        return added;
    }

    // ---------- lifecycle ----------

    @Transactional
    public CampaignItem start(UUID orgId, UUID actorId, UUID id) {
        Campaign c = find(orgId, id);
        if (c.getAccountId() == null) throw ApiException.business("Select a sending account before starting");
        long remaining = recipients.countRemaining(c.getId());
        if (remaining == 0) throw ApiException.business("Add recipients before starting the campaign");
        c.setStatus("RUNNING");
        c.setScheduledAt(Instant.now());
        // enroll all pending recipients into step 1, honoring the sending window
        List<CampaignStep> stepList = steps.findByCampaignIdOrderByPositionAsc(c.getId());
        if (stepList.isEmpty()) throw ApiException.business("Campaign has no steps");
        Instant firstSend = nextAllowedSendTime(orgId);
        recipients.findByCampaignIdOrderByCreatedAtAsc(c.getId(), PageRequest.of(0, Integer.MAX_VALUE)).forEach(r -> {
            if ("PENDING".equals(r.getStatus())) {
                r.setStatus("IN_PROGRESS");
                r.setCurrentStep(0);
                r.setNextSendAt(firstSend);
                recipients.save(r);
            }
        });
        campaigns.save(c);
        auditHelper(c, actorId, "CAMPAIGN_START");
        return toItem(c);
    }

    @Transactional
    public CampaignItem pause(UUID orgId, UUID id) {
        Campaign c = find(orgId, id);
        if (!"RUNNING".equals(c.getStatus())) throw ApiException.business("Only running campaigns can be paused");
        c.setStatus("PAUSED");
        campaigns.save(c);
        return toItem(c);
    }

    @Transactional
    public CampaignItem resume(UUID orgId, UUID id) {
        Campaign c = find(orgId, id);
        if (!"PAUSED".equals(c.getStatus())) throw ApiException.business("Only paused campaigns can be resumed");
        c.setStatus("RUNNING");
        campaigns.save(c);
        return toItem(c);
    }

    @Transactional
    public CampaignItem cancel(UUID orgId, UUID id) {
        Campaign c = find(orgId, id);
        c.setStatus("CANCELLED");
        c.setCompletedAt(Instant.now());
        campaigns.save(c);
        recipients.findByCampaignIdOrderByCreatedAtAsc(c.getId(), PageRequest.of(0, Integer.MAX_VALUE)).forEach(r -> {
            if ("IN_PROGRESS".equals(r.getStatus()) || "PENDING".equals(r.getStatus())) {
                r.setStatus("SKIPPED");
                recipients.save(r);
            }
        });
        return toItem(c);
    }

    // ---------- worker ----------

    /**
     * Advances due recipients. Called by CampaignWorker on a schedule and safe to run repeatedly.
     * Batched to keep transactions short; scale by running more worker instances (rows lock by update).
     */
    @Transactional
    public int processDueBatch(int batchLimit) {
        List<CampaignRecipient> due = recipients.findDueBatch(Instant.now(), PageRequest.of(0, batchLimit));
        int sent = 0;
        for (CampaignRecipient r : due) {
            try {
                sent += advance(r) ? 1 : 0;
            } catch (Exception e) {
                log.warn("Campaign send failed for recipient {}: {}", r.getId(), e.getMessage());
                r.setErrorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(1000, e.getMessage().length())) : "failed");
                r.setStatus("FAILED");
                recipients.save(r);
            }
        }
        return sent;
    }

    private boolean advance(CampaignRecipient r) {
        Campaign c = campaigns.findById(r.getCampaignId()).orElse(null);
        if (c == null || !"RUNNING".equals(c.getStatus()) || c.getDeletedAt() != null) {
            return false; // paused/cancelled: wait
        }
        // suppression re-check at send time
        if (dispatch.isSuppressed(r.getOrganizationId(), r.getEmail())) {
            r.setStatus("UNSUBSCRIBED");
            c.setUnsubscribeCount(c.getUnsubscribeCount() + 1);
            recipients.save(r);
            campaigns.save(c);
            return false;
        }
        List<CampaignStep> stepList = steps.findByCampaignIdOrderByPositionAsc(c.getId());
        int nextIdx = r.getCurrentStep() == null ? 0 : r.getCurrentStep();
        if (nextIdx >= stepList.size()) {
            finish(r, c);
            return false;
        }
        CampaignStep step = stepList.get(nextIdx);
        Lead lead = leads.findInOrg(r.getOrganizationId(), r.getLeadId()).orElse(null);
        if (lead == null) {
            r.setStatus("SKIPPED");
            recipients.save(r);
            return false;
        }
        var template = templates.findById(step.getTemplateId()).orElse(null);
        if (template == null) {
            r.setStatus("FAILED");
            r.setErrorMessage("Template missing");
            recipients.save(r);
            return false;
        }
        Map<String, Object> vars = EmailService.variablesFor(lead);
        String subject = EmailService.render(template.getSubject(), vars);
        String html = EmailService.render(template.getBodyHtml(), vars);
        String text = EmailService.render(template.getBodyText(), vars);

        // daily limit guard per sending account
        EmailAccount account = c.getAccountId() != null ? accounts.findById(c.getAccountId()).orElse(null) : null;
        if (account == null) {
            r.setStatus("FAILED");
            r.setErrorMessage("Campaign account missing");
            recipients.save(r);
            return false;
        }
        long sentToday = messages.countByOrganizationIdAndAccountIdAndStatusAndSentAtBetween(
            r.getOrganizationId(), account.getId(), EmailMessage.Status.SENT,
            Instant.now().minus(java.time.Duration.ofHours(24)), Instant.now());
        if (sentToday >= account.getDailyLimit()) {
            r.setNextSendAt(Instant.now().plus(java.time.Duration.ofHours(1))); // retry later
            recipients.save(r);
            return false;
        }

        // record the message first, then dispatch
        EmailMessage m = new EmailMessage();
        m.setOrganizationId(r.getOrganizationId());
        m.setAccountId(account.getId());
        m.setUserId(account.getUserId());
        m.setLeadId(r.getLeadId());
        m.setCompanyId(lead.getCompanyId());
        m.setContactId(lead.getContactId());
        m.setCampaignId(c.getId());
        m.setDirection(EmailMessage.Direction.OUTBOUND);
        m.setFromEmail(account.getEmail());
        m.setToEmails(List.of(r.getEmail()));
        m.setSubject(subject);
        m.setTrackingId(UUID.randomUUID().toString());
        m.setBodyHtml(EmailService.addTrackingPixel(html, m.getTrackingId()));
        m.setBodyText(text);
        try {
            dispatch.dispatch(r.getOrganizationId(), account.getId(), List.of(r.getEmail()), null, subject, m.getBodyHtml(), text);
            m.setStatus(EmailMessage.Status.SENT);
            m.setSentAt(Instant.now());
            messages.save(m);
            r.setLastEmailId(m.getId());
            r.setCurrentStep(nextIdx + 1);
            c.setSentCount(c.getSentCount() + 1);
            activities.record(r.getOrganizationId(), ActivityType.EMAIL, r.getLeadId(),
                "Campaign \"" + c.getName() + "\": " + subject, text, Map.of("campaignId", c.getId().toString(), "step", nextIdx + 1), null);
        } catch (Exception e) {
            m.setStatus(EmailMessage.Status.FAILED);
            m.setErrorMessage(e.getMessage());
            messages.save(m);
            r.setStatus("FAILED");
            r.setErrorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(1000, e.getMessage().length())) : "send failed");
            recipients.save(r);
            campaigns.save(c);
            return false;
        }

        if (nextIdx + 1 >= stepList.size()) {
            finish(r, c);
        } else {
            r.setNextSendAt(nextAllowedSendTime(r.getOrganizationId()).plus(java.time.Duration.ofDays(stepList.get(nextIdx + 1).getDelayDays())));
            recipients.save(r);
        }
        campaigns.save(c);
        return true;
    }

    private void finish(CampaignRecipient r, Campaign c) {
        r.setStatus("COMPLETED");
        r.setNextSendAt(null);
        recipients.save(r);
        if (recipients.countRemaining(c.getId()) == 0 && "RUNNING".equals(c.getStatus())) {
            c.setStatus("COMPLETED");
            c.setCompletedAt(Instant.now());
            campaigns.save(c);
        }
    }

    /** Honors the organization sending window (default 08:00–18:00 UTC). */
    private Instant nextAllowedSendTime(UUID orgId) {
        Map<String, Object> window = settings.get(orgId);
        @SuppressWarnings("unchecked")
        Map<String, Object> w = (Map<String, Object>) window.get("sendingWindow");
        int startHour = ((Number) w.getOrDefault("startHour", 8)).intValue();
        int endHour = ((Number) w.getOrDefault("endHour", 18)).intValue();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (now.getHour() < startHour) {
            return now.toLocalDate().atStartOfDay(ZoneOffset.UTC).plusHours(startHour).toInstant();
        }
        if (now.getHour() >= endHour) {
            return now.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).plusHours(startHour).toInstant();
        }
        return Instant.now();
    }

    @Transactional(readOnly = true)
    public PageResponse<RecipientItem> recipients(UUID orgId, UUID id, int page, int size) {
        find(orgId, id);
        var result = recipients.findByCampaignIdOrderByCreatedAtAsc(id, PageRequest.of(page, Math.min(size, 200)));
        return PageResponse.of(result.map(r -> new RecipientItem(r.getId(), r.getLeadId(), r.getEmail(), r.getStatus(),
            r.getCurrentStep(), r.getNextSendAt(), r.getErrorMessage())));
    }

    private void auditHelper(Campaign c, UUID actorId, String action) {
        activities.record(c.getOrganizationId(), ActivityType.SYSTEM, null,
            action + ": " + c.getName(), null, Map.of("campaignId", c.getId().toString()), actorId);
    }

    private Campaign find(UUID orgId, UUID id) {
        return campaigns.findById(id).filter(c -> c.getOrganizationId().equals(orgId) && c.getDeletedAt() == null)
            .orElseThrow(() -> ApiException.notFound("Campaign not found"));
    }

    public CampaignItem toItem(Campaign c) {
        return new CampaignItem(c.getId(), c.getName(), c.getDescription(), c.getAccountId(),
            c.getAccountId() != null ? accounts.findById(c.getAccountId()).map(EmailAccount::getEmail).orElse(null) : null,
            c.getStatus(), c.getScheduledAt(), c.getTotalRecipients(), c.getSentCount(), c.getOpenCount(),
            c.getReplyCount(), c.getBounceCount(), c.getUnsubscribeCount(),
            steps.findByCampaignIdOrderByPositionAsc(c.getId()).stream()
                .map(s -> new StepItem(s.getId(), s.getPosition(), s.getTemplateId(), s.getDelayDays())).toList(),
            c.getCreatedAt());
    }
}
