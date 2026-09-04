package com.crm.unit;

import com.crm.modules.identity.domain.Permission;
import com.crm.modules.leads.domain.Lead;
import com.crm.modules.leads.domain.ScoringRule;
import com.crm.modules.leads.repo.ScoringRuleRepository;
import com.crm.modules.leads.service.LeadScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class LeadScoringServiceTest {

    private ScoringRuleRepository repo;
    private LeadScoringService service;

    @BeforeEach
    void setup() {
        repo = Mockito.mock(ScoringRuleRepository.class);
        service = new LeadScoringService(repo);
    }

    private ScoringRule rule(String criterion, String operand, int points) {
        ScoringRule r = new ScoringRule();
        r.setCriterion(criterion);
        r.setOperand(operand);
        r.setPoints(points);
        r.setActive(true);
        return r;
    }

    @Test
    void sumsMatchingRulesAndClampsToHundred() {
        when(repo.findByOrganizationIdAndActiveTrueOrderByPositionAsc(any())).thenReturn(List.of(
            rule("HAS_EMAIL", null, 40), rule("HAS_PHONE", null, 40), rule("DECISION_MAKER_TITLE", null, 40)));
        Lead lead = new Lead();
        lead.setEmail("a@b.c");
        lead.setPhone("+971501234567");
        lead.setJobTitle("CEO & Founder");
        assertThat(service.score(UUID.randomUUID(), lead)).isEqualTo(100);
        assertThat(LeadScoringService.category(100)).isEqualTo("VERY_HOT");
    }

    @Test
    void negativePointsApplyAndCategoryCold() {
        when(repo.findByOrganizationIdAndActiveTrueOrderByPositionAsc(any())).thenReturn(List.of(
            rule("HAS_EMAIL", null, 20), rule("STATUS_IS", "UNQUALIFIED", -30)));
        Lead lead = new Lead();
        lead.setEmail("a@b.c");
        lead.setStatus(com.crm.modules.leads.domain.LeadStatus.NEW);
        assertThat(service.score(UUID.randomUUID(), lead)).isEqualTo(20);
        assertThat(LeadScoringService.category(20)).isEqualTo("COLD");
    }

    @Test
    void customFieldAndListRules() {
        when(repo.findByOrganizationIdAndActiveTrueOrderByPositionAsc(any())).thenReturn(List.of(
            rule("CUSTOM_FIELD_IS", "services=IV Therapy", 10),
            rule("CITY_IN", "dubai, abu dhabi", 15)));
        Lead lead = new Lead();
        lead.setCity("Dubai");
        lead.setCustomFields(Map.of("services", "IV Therapy"));
        assertThat(service.score(UUID.randomUUID(), lead)).isEqualTo(25);
    }
}
