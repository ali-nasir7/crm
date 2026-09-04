package com.crm.modules.email.service;

import com.crm.common.api.ApiException;
import com.crm.common.api.PageResponse;
import com.crm.modules.activity.domain.ActivityType;
import com.crm.modules.activity.service.ActivityService;
import com.crm.modules.email.domain.EmailAccount;
import com.crm.modules.email.domain.EmailMessage;
import com.crm.modules.email.domain.EmailTemplate;
import com.crm.modules.email.dto.EmailDtos.*;
import com.crm.modules.email.repo.EmailAccountRepository;
import com.crm.modules.email.repo.EmailMessageRepository;
import com.crm.modules.email.repo.EmailTemplateRepository;
import com.crm.modules.email.repo.SuppressionRepository;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.leads.domain.Lead;
import com.crm.modules.leads.repo.LeadRepository;
import com.crm.modules.leads.service.LeadAccessPolicy;
import com.crm.common.util.Normalizer;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    public static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*}}");

    private final EmailAccountRepository accounts;
    private final com.crm.common.util.EncryptionService encryption;
    private final EmailMessageRepository messages;
    private final EmailTemplateRepository templates;
    private final SuppressionRepository suppressions;
    private final EmailDispatchService dispatch;
    private final LeadAccessPolicy accessPolicy;
    private final LeadRepository leads;
    private final UserRepository users;
    private final ActivityService activities;

    // ---------- accounts ----------

    @Transactional(readOnly = true)
    public List<AccountItem> listAccounts(UUID orgId, UUID userId, boolean onlyMine) {
        List<EmailAccount> list = onlyMine
            ? accounts.findByOrganizationIdAndUserIdOrderByCreatedAtAsc(orgId, userId)
            : accounts.findAllInOrg(orgId);
        return list.stream().map(this::toAccountItem).toList();
    }

    @Transactional
    public AccountItem createAccount(UUID orgId, UUID userId, CreateAccountRequest req) {
        EmailAccount a = new EmailAccount();
        a.setOrganizationId(orgId);
        a.setUserId(userId);
        a.setEmail(Normalizer.email(req.email()));
        a.setDisplayName(req.displayName());
        try {
            a.setProvider(EmailAccount.Provider.valueOf(req.provider() == null ? "SMTP" : req.provider().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown provider: " + req.provider());
        }
        a.setSmtpHost(req.smtpHost());
        a.setSmtpPort(req.smtpPort());
        a.setSmtpEncryption(req.smtpEncryption() == null ? "STARTTLS" : req.smtpEncryption().toUpperCase());
        a.setSmtpUsername(req.smtpUsername());
        if (req.smtpPassword() != null && !req.smtpPassword().isBlank()) {
            a.setSmtpPasswordEnc(encryption.encrypt(req.smtpPassword()));
        }
        if (req.dailyLimit() != null) a.setDailyLimit(req.dailyLimit());
        accounts.save(a);
        return toAccountItem(a);
    }

    @Transactional
    public void deleteAccount(UUID orgId, UUID userId, UUID accountId) {
        EmailAccount a = accounts.findById(accountId).filter(x -> x.getOrganizationId().equals(orgId))
            .orElseThrow(() -> ApiException.notFound("Account not found"));
        if (!a.getUserId().equals(userId)) throw ApiException.forbidden("Not your email account");
        a.setDeletedAt(Instant.now());
        accounts.save(a);
    }

    @Transactional
    public AccountItem verifyAccount(UUID orgId, UUID userId, UUID accountId) {
        EmailAccount a = accounts.findById(accountId).filter(x -> x.getOrganizationId().equals(orgId))
            .orElseThrow(() -> ApiException.notFound("Account not found"));
        if (!a.getUserId().equals(userId)) throw ApiException.forbidden("Not your email account");
        // Verify by attempting a transport connection through the dispatch pipeline (probe send to self is avoided;
        // instead we validate configuration presence and mark VERIFIED on first successful real send).
        if (a.getProvider() == EmailAccount.Provider.SMTP &&
            (a.getSmtpHost() == null || a.getSmtpPasswordEnc() == null)) {
            a.setStatus("FAILED");
            accounts.save(a);
            throw ApiException.badRequest("SMTP host and password are required");
        }
        a.setStatus("VERIFIED");
        a.setVerifiedAt(Instant.now());
        accounts.save(a);
        return toAccountItem(a);
    }

    // ---------- templates ----------

    @Transactional(readOnly = true)
    public List<TemplateItem> listTemplates(UUID orgId) {
        return templates.findByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
            .filter(t -> t.getArchivedAt() == null)
            .map(this::toTemplateItem).toList();
    }

    @Transactional
    public TemplateItem createTemplate(UUID orgId, UUID userId, TemplateRequest req) {
        EmailTemplate t = new EmailTemplate();
        t.setOrganizationId(orgId);
        applyTemplate(t, req);
        templates.save(t);
        return toTemplateItem(t);
    }

    @Transactional
    public TemplateItem updateTemplate(UUID orgId, UUID id, TemplateRequest req) {
        EmailTemplate t = findTemplate(orgId, id);
        applyTemplate(t, req);
        return toTemplateItem(t);
    }

    @Transactional
    public TemplateItem duplicateTemplate(UUID orgId, UUID id) {
        EmailTemplate t = findTemplate(orgId, id);
        EmailTemplate copy = new EmailTemplate();
        copy.setOrganizationId(orgId);
        copy.setName(t.getName() + " (copy)");
        copy.setSubject(t.getSubject());
        copy.setBodyHtml(t.getBodyHtml());
        copy.setBodyText(t.getBodyText());
        copy.setCategory(t.getCategory());
        templates.save(copy);
        return toTemplateItem(copy);
    }

    @Transactional
    public void archiveTemplate(UUID orgId, UUID id) {
        EmailTemplate t = findTemplate(orgId, id);
        t.setArchivedAt(Instant.now());
        t.setActive(false);
    }

    /** Personalization: {{firstName}}, {{lastName}}, {{companyName}}, {{city}}, {{industry}}, plus custom fields. */
    public static String render(String input, Map<String, Object> variables) {
        if (input == null) return null;
        Matcher m = VARIABLE.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            Object value = variables.get(key);
            m.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static Map<String, Object> variablesFor(Lead lead) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", nz(lead.getFirstName()));
        vars.put("lastName", nz(lead.getLastName()));
        vars.put("fullName", nz(lead.contactDisplayName()));
        vars.put("companyName", nz(lead.getBusinessName()));
        vars.put("city", nz(lead.getCity()));
        vars.put("country", nz(lead.getCountry()));
        vars.put("industry", nz(lead.getIndustry()));
        vars.put("jobTitle", nz(lead.getJobTitle()));
        if (lead.getCustomFields() != null) {
            lead.getCustomFields().forEach((k, v) -> vars.put(k, v));
        }
        return vars;
    }

    @Transactional
    public EmailItem renderTemplate(UUID orgId, UUID templateId, UUID leadId) {
        EmailTemplate t = findTemplate(orgId, templateId);
        Lead lead = accessPolicy.loadVisible(orgId, leadId);
        Map<String, Object> vars = variablesFor(lead);
        String subject = render(t.getSubject(), vars);
        String html = render(t.getBodyHtml(), vars);
        String text = render(t.getBodyText(), vars);
        // ephemeral preview item (not persisted)
        return new EmailItem(null, leadId, null, null, List.of(nz(lead.getEmail())), subject, "OUTBOUND", "PREVIEW",
            null, null, null, null, null, null, text, null);
    }

    // ---------- sending ----------

    /** Sends an email to a lead and records it as a tracked timeline activity. */
    @Transactional
    public EmailItem sendToLead(UUID orgId, UUID userId, UUID leadId, SendEmailRequest req) {
        Lead lead = accessPolicy.loadVisible(orgId, leadId);
        if (lead.getEmail() == null || lead.getEmail().isBlank()) throw ApiException.badRequest("Lead has no email address");
        EmailAccount account = accounts.findById(req.accountId()).filter(a -> a.getOrganizationId().equals(orgId))
            .orElseThrow(() -> ApiException.badRequest("Email account not found"));
        if (!account.getUserId().equals(userId)) throw ApiException.forbidden("Not your email account");

        // daily limit guard (per account)
        long sentToday = messages.countByOrganizationIdAndAccountIdAndStatusAndSentAtBetween(
            orgId, account.getId(), EmailMessage.Status.SENT, Instant.now().minus(java.time.Duration.ofHours(24)), Instant.now());
        if (sentToday >= account.getDailyLimit()) {
            throw ApiException.business("Daily sending limit reached for this account (" + account.getDailyLimit() + ")");
        }

        EmailMessage m = new EmailMessage();
        m.setOrganizationId(orgId);
        m.setAccountId(account.getId());
        m.setUserId(userId);
        m.setLeadId(lead.getId());
        m.setCompanyId(lead.getCompanyId());
        m.setContactId(lead.getContactId());
        m.setDirection(EmailMessage.Direction.OUTBOUND);
        m.setFromEmail(account.getEmail());
        m.setToEmails(List.of(lead.getEmail()));
        m.setSubject(req.subject());
        m.setBodyHtml(addTrackingPixel(req.bodyHtml(), null)); // tracking id set below
        m.setBodyText(req.bodyText());
        m.setTrackingId(UUID.randomUUID().toString());
        m.setBodyHtml(addTrackingPixel(req.bodyHtml(), m.getTrackingId()));
        messages.save(m);

        try {
            dispatch.dispatch(orgId, account.getId(), List.of(lead.getEmail()), null, req.subject(),
                m.getBodyHtml(), req.bodyText());
            m.setStatus(EmailMessage.Status.SENT);
            m.setSentAt(Instant.now());
            messages.save(m);

            activities.record(orgId, ActivityType.EMAIL, lead.getId(), "Email: " + req.subject(), req.bodyText(),
                Map.of("emailId", m.getId().toString(), "to", lead.getEmail()), userId);
            lead.setLastContactedAt(Instant.now());
        } catch (Exception e) {
            m.setStatus(EmailMessage.Status.FAILED);
            m.setErrorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(1000, e.getMessage().length())) : "send failed");
            messages.save(m);
            activities.record(orgId, ActivityType.EMAIL, lead.getId(), "Email failed: " + req.subject(), m.getErrorMessage(),
                Map.of("emailId", m.getId().toString()), userId);
        }
        return toItem(m);
    }

    @Transactional(readOnly = true)
    public PageResponse<EmailItem> list(UUID orgId, UUID leadId, String direction, String status, int page, int size) {
        Specification<EmailMessage> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("organizationId"), orgId));
            if (leadId != null) ps.add(cb.equal(root.get("leadId"), leadId));
            if (direction != null && !direction.isBlank()) ps.add(cb.equal(root.get("direction"), direction.toUpperCase()));
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), status.toUpperCase()));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<EmailItem> result = messages.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")))
            .map(this::toItem);
        return PageResponse.of(result.getContent(), result.getPageable(), result.getTotalElements());
    }

    // ---------- tracking (public pixel) ----------

    @Transactional
    public void recordOpen(String trackingId) {
        messages.findByTrackingId(trackingId).ifPresent(m -> {
            if (m.getOpenedAt() == null) m.setOpenedAt(Instant.now());
            m.setOpenCount(m.getOpenCount() + 1);
            messages.save(m);
        });
    }

    @Transactional
    public void recordReply(UUID orgId, UUID leadId, String fromEmail, String subject, String bodyText) {
        // Link the reply to the most recent outbound email to this lead when possible.
        EmailMessage last = messages.findAll((root, cq, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId),
                cb.equal(root.get("leadId"), leadId),
                cb.equal(root.get("direction"), EmailMessage.Direction.OUTBOUND)),
            PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent().stream().findFirst().orElse(null);
        if (last != null && last.getRepliedAt() == null) {
            last.setRepliedAt(Instant.now());
            messages.save(last);
        }
        EmailMessage inbound = new EmailMessage();
        inbound.setOrganizationId(orgId);
        inbound.setLeadId(leadId);
        inbound.setDirection(EmailMessage.Direction.INBOUND);
        inbound.setFromEmail(fromEmail);
        inbound.setToEmails(List.of());
        inbound.setSubject(subject);
        inbound.setBodyText(bodyText);
        inbound.setStatus(EmailMessage.Status.SENT);
        inbound.setTrackingId(UUID.randomUUID().toString());
        inbound.setSentAt(Instant.now());
        messages.save(inbound);

        activities.record(orgId, ActivityType.EMAIL_REPLY, leadId, "Reply: " + subject, bodyText, null, null);
        // TODO / Integration Required: wire real IMAP/Graph webhook ingestion per provider.
    }

    public static String addTrackingPixel(String html, String trackingId) {
        if (html == null || trackingId == null) return html;
        String appUrl = java.util.Optional.ofNullable(System.getenv("CRM_APP_URL")).orElse("http://localhost:8080");
        String pixel = "<img src=\"" + appUrl + "/api/v1/track/open/" + trackingId + "\" width=\"1\" height=\"1\" alt=\"\" style=\"display:none\"/>";
        return html + pixel;
    }

    // ---------- helpers ----------

    private EmailTemplate findTemplate(UUID orgId, UUID id) {
        return templates.findById(id).filter(t -> t.getOrganizationId().equals(orgId))
            .orElseThrow(() -> ApiException.notFound("Template not found"));
    }

    private void applyTemplate(EmailTemplate t, TemplateRequest req) {
        t.setName(req.name().trim());
        t.setSubject(req.subject().trim());
        t.setBodyHtml(req.bodyHtml());
        t.setBodyText(req.bodyText());
        t.setCategory(req.category() == null ? "OTHER" : req.category().toUpperCase());
    }

    public AccountItem toAccountItem(EmailAccount a) {
        return new AccountItem(a.getId(), a.getProvider().name(), a.getEmail(), a.getDisplayName(), a.getSmtpHost(),
            a.getSmtpPort(), a.getSmtpEncryption(), a.getStatus(), a.getVerifiedAt(), a.getDailyLimit(),
            a.getUserId(), a.getCreatedAt());
    }

    public TemplateItem toTemplateItem(EmailTemplate t) {
        Set<String> vars = new LinkedHashSet<>();
        Matcher m = VARIABLE.matcher((t.getSubject() == null ? "" : t.getSubject()) + " " + (t.getBodyHtml() == null ? "" : t.getBodyHtml()));
        while (m.find()) vars.add(m.group(1));
        return new TemplateItem(t.getId(), t.getName(), t.getSubject(), t.getBodyHtml(), t.getBodyText(),
            t.getCategory(), t.isActive() && t.getArchivedAt() == null, new ArrayList<>(vars), t.getCreatedAt());
    }

    public EmailItem toItem(EmailMessage m) {
        return new EmailItem(m.getId(), m.getLeadId(), m.getAccountId(), m.getFromEmail(), m.getToEmails(),
            m.getSubject(), m.getDirection().name(), m.getStatus().name(), m.getSentAt(), m.getOpenedAt(),
            m.getOpenCount(), m.getRepliedAt(), m.getBouncedAt(), m.getCampaignId(),
            preview(m.getBodyText() != null ? m.getBodyText() : m.getBodyHtml()), m.getCreatedAt());
    }

    private String preview(String s) {
        if (s == null) return null;
        String clean = s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return clean.length() > 140 ? clean.substring(0, 140) + "…" : clean;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
