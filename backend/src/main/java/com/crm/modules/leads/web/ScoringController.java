package com.crm.modules.leads.web;

import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.leads.domain.ScoringRule;
import com.crm.modules.leads.dto.LeadDtos.ScoringRuleRequest;
import com.crm.modules.leads.repo.ScoringRuleRepository;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scoring-rules")
@RequiredArgsConstructor
@Tag(name = "Lead Scoring")
public class ScoringController {

    private final ScoringRuleRepository rules;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.SCORING_VIEW + "')")
    public List<ScoringRule> list() {
        return rules.findByOrganizationIdOrderByPositionAsc(CurrentUser.require().getOrganizationId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.SCORING_UPDATE + "')")
    public ScoringRule create(@Valid @RequestBody ScoringRuleRequest request) {
        ScoringRule r = new ScoringRule();
        r.setOrganizationId(CurrentUser.require().getOrganizationId());
        r.setCriterion(request.criterion());
        r.setOperand(request.operand());
        r.setPoints(request.points());
        r.setLabel(request.label());
        r.setActive(request.active());
        r.setPosition(rules.findByOrganizationIdOrderByPositionAsc(CurrentUser.require().getOrganizationId()).size());
        return rules.save(r);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.SCORING_UPDATE + "')")
    public ScoringRule update(@PathVariable UUID id, @Valid @RequestBody ScoringRuleRequest request) {
        ScoringRule r = rules.findById(id).orElseThrow();
        r.setCriterion(request.criterion());
        r.setOperand(request.operand());
        r.setPoints(request.points());
        r.setLabel(request.label());
        r.setActive(request.active());
        return rules.save(r);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.SCORING_UPDATE + "')")
    public void delete(@PathVariable UUID id) {
        rules.deleteById(id);
    }
}
