package com.crm.modules.automation.web;

import com.crm.modules.automation.domain.AutomationRule;
import com.crm.modules.automation.repo.AutomationRunRepository;
import com.crm.modules.automation.repo.AutomationRuleRepository;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/automations")
@RequiredArgsConstructor
@Tag(name = "Automations")
public class AutomationController {

    private final AutomationRuleRepository rules;
    private final AutomationRunRepository runs;

    public record AutomationRequest(String name, String trigger, Map<String, Object> conditions,
                                    String action, Map<String, Object> actionConfig, boolean active) {}

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.AUTOMATION_VIEW + "')")
    public List<AutomationRule> list() {
        return rules.findByOrganizationIdOrderByCreatedAtDesc(CurrentUser.require().getOrganizationId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.AUTOMATION_UPDATE + "')")
    public AutomationRule create(@RequestBody AutomationRequest request) {
        AutomationRule r = new AutomationRule();
        r.setOrganizationId(CurrentUser.require().getOrganizationId());
        r.setCreatedBy(CurrentUser.require().getId());
        apply(r, request);
        return rules.save(r);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.AUTOMATION_UPDATE + "')")
    public AutomationRule update(@PathVariable UUID id, @RequestBody AutomationRequest request) {
        AutomationRule r = rules.findById(id).filter(x -> x.getOrganizationId().equals(CurrentUser.require().getOrganizationId()))
            .orElseThrow(() -> com.crm.common.api.ApiException.notFound("Automation not found"));
        apply(r, request);
        return rules.save(r);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.AUTOMATION_UPDATE + "')")
    public void delete(@PathVariable UUID id) {
        rules.findById(id).filter(x -> x.getOrganizationId().equals(CurrentUser.require().getOrganizationId()))
            .ifPresent(rules::delete);
    }

    @GetMapping("/{id}/runs")
    @PreAuthorize("hasAuthority('" + PermissionKeys.AUTOMATION_VIEW + "')")
    public List<com.crm.modules.automation.domain.AutomationRun> runs(@PathVariable UUID id) {
        return runs.findByRuleIdOrderByCreatedAtDesc(id, PageRequest.of(0, 50));
    }

    private void apply(AutomationRule r, AutomationRequest request) {
        r.setName(request.name());
        r.setTrigger(AutomationRule.Trigger.valueOf(request.trigger().toUpperCase()));
        r.setConditions(request.conditions());
        r.setAction(AutomationRule.Action.valueOf(request.action().toUpperCase()));
        r.setActionConfig(request.actionConfig());
        r.setActive(request.active());
    }
}
