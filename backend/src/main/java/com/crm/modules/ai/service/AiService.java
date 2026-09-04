package com.crm.modules.ai.service;

import com.crm.modules.ai.domain.AiAction;
import com.crm.modules.ai.repo.AiActionRepository;
import com.crm.modules.calls.repo.CallRepository;
import com.crm.modules.email.repo.EmailMessageRepository;
import com.crm.modules.leads.domain.Lead;
import com.crm.modules.leads.repo.LeadRepository;
import com.crm.modules.leads.service.LeadAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI-assisted sales layer (§16). Assistant, not autonomous: every output is a reviewable draft,
 * logged to ai_actions, and nothing is ever sent without an explicit human action.
 */
@Service
@RequiredArgsConstructor
public class AiService {

    private final List<AiProvider> providers;
    private final LeadRepository leads;
    private final LeadAccessPolicy accessPolicy;
    private final CallRepository calls;
    private final EmailMessageRepository emails;
    private final AiActionRepository aiActions;

    @Transactional(readOnly = true)
    public Map<String, Object> leadSummary(UUID orgId, UUID leadId) {
        Lead lead = accessPolicy.loadVisible(orgId, leadId);
        String analysis = analyze(lead);

        String prompt = """
            Summarize this lead for a salesperson.
            Company: %s | Industry: %s | Location: %s | Contact: %s (%s)
            Website quality signals: %s | Custom data: %s
            Recent activity: %s
            Answer: who is this company, what do they do, why could they be a customer, and what should the salesperson say first?
            """.formatted(nz(lead.getBusinessName()), nz(lead.getIndustry()), nz(lead.getCity()) + " " + nz(lead.getCountry()),
            nz(lead.contactDisplayName()), nz(lead.getJobTitle()), nz(lead.getWebsite()), lead.getCustomFields(), analysis);

        AiProvider.Result result = complete("LEAD_SUMMARY", prompt);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("leadId", leadId);
        out.put("summary", result.text());
        out.put("deterministic", heuristicSummary(lead, analysis));
        out.put("provider", result.provider());
        return logAndReturn(orgId, "LEAD_SUMMARY", leadId, result, out);
    }

    @Transactional
    public Map<String, Object> emailDraft(UUID orgId, UUID userId, UUID leadId, String useCase) {
        Lead lead = accessPolicy.loadVisible(orgId, leadId);
        Map<String, Object> vars = com.crm.modules.email.service.EmailService.variablesFor(lead);
        AiProvider.Result result = complete(useCase.toUpperCase(), "Write a " + useCase + " email for " + lead.getBusinessName());
        String rendered = com.crm.modules.email.service.EmailService.render(result.text(), vars);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("leadId", leadId);
        out.put("useCase", useCase.toUpperCase());
        out.put("subject", subjectFor(useCase, lead));
        out.put("body", rendered);
        out.put("provider", result.provider());
        out.put("fallbackUsed", result.fallbackUsed());
        return logAndReturn(orgId, "EMAIL_DRAFT", leadId, result, out);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> nextBestAction(UUID orgId, UUID leadId) {
        Lead lead = accessPolicy.loadVisible(orgId, leadId);
        long callRows = calls.count((root, cq, cb) -> cb.and(
            cb.equal(root.get("organizationId"), orgId), cb.equal(root.get("leadId"), leadId)));
        long outboundEmails = emails.count((root, cq, cb) -> cb.and(
            cb.equal(root.get("organizationId"), orgId), cb.equal(root.get("leadId"), leadId),
            cb.equal(root.get("direction"), com.crm.modules.email.domain.EmailMessage.Direction.OUTBOUND)));
        long replies = emails.count((root, cq, cb) -> cb.and(
            cb.equal(root.get("organizationId"), orgId), cb.equal(root.get("leadId"), leadId),
            cb.isNotNull(root.get("repliedAt"))));

        String action;
        String rationale;
        if (callRows == 0 && outboundEmails == 0) {
            action = "Make the first call within 24 hours — the lead is untouched.";
            rationale = "No calls or emails recorded yet.";
        } else if (replies > 0 && lead.getStatus() != com.crm.modules.leads.domain.LeadStatus.QUALIFIED) {
            action = "They replied — qualify now and propose a discovery call.";
            rationale = replies + " inbound reply detected and status is " + lead.getStatus() + ".";
        } else if (outboundEmails >= 2 && replies == 0) {
            action = "Pause email sequence; try a phone touch or a re-engagement angle.";
            rationale = outboundEmails + " outbound emails without a reply.";
        } else if (lead.getNextFollowUpAt() != null && lead.getNextFollowUpAt().isBefore(java.time.Instant.now())) {
            action = "Follow-up is overdue — call today and log the outcome.";
            rationale = "nextFollowUpAt is in the past.";
        } else if (lead.getScore() >= 50) {
            action = "Hot lead: schedule a demo and prepare a proposal draft.";
            rationale = "Lead score " + lead.getScore() + " (" + com.crm.modules.leads.service.LeadScoringService.category(lead.getScore()) + ").";
        } else {
            action = "Send a value-first follow-up and set a 3-day reminder.";
            rationale = "Standard cadence; score " + lead.getScore() + ".";
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("leadId", leadId);
        out.put("action", action);
        out.put("rationale", rationale);
        out.put("stats", Map.of("calls", callRows, "outboundEmails", outboundEmails, "replies", replies));
        out.put("provider", "rule-based");
        return logAndReturn(orgId, "NEXT_ACTION", leadId, new AiProvider.Result("rule-based", action, true), out);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(UUID orgId) {
        return aiActions.findTop50ByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
            .map(a -> Map.<String, Object>of(
                "id", a.getId(), "useCase", a.getUseCase(), "leadId", nz(a.getLeadId()),
                "provider", a.getProvider(), "output", a.getOutput(), "createdAt", a.getCreatedAt()))
            .toList();
    }

    // ---------- internals ----------

    private AiProvider.Result complete(String useCase, String userPrompt) {
        AiProvider.Result fallback = null;
        for (AiProvider provider : providers) {
            if (!provider.isAvailable()) continue;
            AiProvider.Result result = provider.complete(new AiProvider.Request(useCase,
                "You are a concise B2B sales assistant inside a CRM. Output plain text only.", userPrompt, 600));
            if (!result.fallbackUsed()) return result;
            fallback = result;
        }
        return fallback != null ? fallback : new AiProvider.Result("none", "AI provider unavailable", true);
    }

    private String analyze(Lead lead) {
        long callRows = calls.count((root, cq, cb) -> cb.and(
            cb.equal(root.get("organizationId"), lead.getOrganizationId()), cb.equal(root.get("leadId"), lead.getId())));
        long emailRows = emails.count((root, cq, cb) -> cb.and(
            cb.equal(root.get("organizationId"), lead.getOrganizationId()), cb.equal(root.get("leadId"), lead.getId())));
        return "calls=" + callRows + ", emails=" + emailRows + ", score=" + lead.getScore() +
            ", status=" + lead.getStatus() + (lead.getNotes() == null ? "" : ", notes=" + lead.getNotes());
    }

    private Map<String, Object> heuristicSummary(Lead lead, String analysis) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("who", lead.getBusinessName() + (lead.getIndustry() == null ? "" : " (" + lead.getIndustry() + ")") +
            (lead.getCity() == null ? "" : " in " + lead.getCity() + ", " + nz(lead.getCountry())));
        m.put("contact", nz(lead.contactDisplayName()) + (lead.getJobTitle() == null ? "" : " — " + lead.getJobTitle()));
        m.put("whyPotentialFit", "Profile matches targeting criteria: industry, location and size data present; score " +
            lead.getScore() + " (" + com.crm.modules.leads.service.LeadScoringService.category(lead.getScore()) + ").");
        m.put("opener", "Reference their " + (lead.getWebsite() != null ? "website" : "market position") +
            " and ask about their current follow-up process.");
        m.put("history", analysis);
        return m;
    }

    private String subjectFor(String useCase, Lead lead) {
        return switch (useCase.toLowerCase()) {
            case "follow_up" -> "Following up — " + lead.getBusinessName();
            case "meeting_confirmation" -> "Confirming our call — " + lead.getBusinessName();
            case "re_engagement" -> "Reconnecting with " + lead.getBusinessName();
            default -> "Quick idea for " + lead.getBusinessName();
        };
    }

    private Map<String, Object> logAndReturn(UUID orgId, String useCase, UUID leadId, AiProvider.Result result, Map<String, Object> out) {
        AiAction action = new AiAction();
        action.setOrganizationId(orgId);
        action.setLeadId(leadId);
        action.setUseCase(useCase);
        action.setProvider(result.provider());
        action.setOutput(out);
        // userId filled by caller when in request scope
        com.crm.security.UserPrincipal p = com.crm.security.CurrentUser.principalOrNull();
        if (p != null) action.setUserId(p.getId());
        aiActions.save(action);
        return out;
    }

    private static String nz(Object o) { return o == null ? "" : String.valueOf(o); }
}
