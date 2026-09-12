package com.crm.modules.leads.service;

import com.crm.modules.leads.domain.Lead;
import com.crm.modules.leads.domain.ScoringRule;
import com.crm.modules.leads.repo.ScoringRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Configurable scoring engine. Rules are data (per org); evaluation is deterministic:
 * every active rule whose criterion matches contributes its (possibly negative) points.
 * Clamped to 0..100.
 */
@Service
@RequiredArgsConstructor
public class LeadScoringService {

    public static final List<String> DECISION_TITLES = List.of(
        "owner", "ceo", "founder", "co-founder", "director", "partner", "president", "cmo", "cto", "ceo & founder", "managing director");

    private final ScoringRuleRepository rules;

    public int score(UUID orgId, Lead lead) {
        List<ScoringRule> active = rules.findByOrganizationIdAndActiveTrueOrderByPositionAsc(orgId);
        int total = 0;
        for (ScoringRule rule : active) {
            if (matches(rule, lead)) total += rule.getPoints();
        }
        return Math.max(0, Math.min(100, total));
    }

    public boolean matches(ScoringRule rule, Lead lead) {
        String operand = rule.getOperand() == null ? "" : rule.getOperand();
        List<String> values = List.of(operand.toLowerCase(Locale.ROOT).split("\\s*,\\s*"));
        String title = lead.getJobTitle() == null ? "" : lead.getJobTitle().toLowerCase(Locale.ROOT);
        return switch (rule.getCriterion()) {
            case "HAS_EMAIL" -> lead.getEmail() != null && !lead.getEmail().isBlank();
            case "HAS_PHONE" -> lead.getPhone() != null && !lead.getPhone().isBlank();
            case "HAS_WHATSAPP" -> lead.getWhatsapp() != null && !lead.getWhatsapp().isBlank();
            case "HAS_WEBSITE" -> lead.getWebsite() != null && !lead.getWebsite().isBlank();
            case "HAS_LINKEDIN" -> lead.getLinkedin() != null && !lead.getLinkedin().isBlank();
            case "DECISION_MAKER_TITLE" -> DECISION_TITLES.stream().anyMatch(title::contains);
            case "INDUSTRY_IN" -> lead.getIndustry() != null && values.contains(lead.getIndustry().toLowerCase(Locale.ROOT));
            case "COUNTRY_IN" -> lead.getCountry() != null && values.contains(lead.getCountry().toLowerCase(Locale.ROOT));
            case "CITY_IN" -> lead.getCity() != null && values.contains(lead.getCity().toLowerCase(Locale.ROOT));
            case "COMPANY_SIZE_IN" -> lead.getCompanySize() != null && values.contains(lead.getCompanySize().toLowerCase(Locale.ROOT));
            case "EMPLOYEES_MIN" -> lead.getEmployeesCount() != null && lead.getEmployeesCount() >= num(operand, 0);
            case "SOURCE_IS" -> operand.equalsIgnoreCase(String.valueOf(lead.getSourceId()));
            case "STATUS_IS" -> lead.getStatus() != null && lead.getStatus().name().equalsIgnoreCase(operand);
            case "CUSTOM_FIELD_IS" -> {
                String[] kv = operand.split("=", 2);
                if (kv.length != 2) yield false;
                Object v = lead.getCustomFields() == null ? null : lead.getCustomFields().get(kv[0].trim());
                yield v != null && String.valueOf(v).equalsIgnoreCase(kv[1].trim());
            }
            default -> false;
        };
    }

    public static String category(int score) {
        if (score >= 75) return "VERY_HOT";
        if (score >= 50) return "HOT";
        if (score >= 25) return "WARM";
        return "COLD";
    }

    private int num(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }
}
