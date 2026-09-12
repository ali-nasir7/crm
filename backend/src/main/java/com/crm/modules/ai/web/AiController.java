package com.crm.modules.ai.web;

import com.crm.modules.ai.service.AiService;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Assistant")
public class AiController {

    private final AiService aiService;

    @PostMapping("/lead-summary/{leadId}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.AI_USE + "')")
    public Map<String, Object> leadSummary(@PathVariable UUID leadId) {
        return aiService.leadSummary(CurrentUser.require().getOrganizationId(), leadId);
    }

    @PostMapping("/email-draft")
    @PreAuthorize("hasAuthority('" + PermissionKeys.AI_USE + "')")
    public Map<String, Object> emailDraft(@RequestParam UUID leadId, @RequestParam(defaultValue = "EMAIL_OUTREACH") String useCase) {
        return aiService.emailDraft(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), leadId, useCase);
    }

    @PostMapping("/next-action/{leadId}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.AI_USE + "')")
    public Map<String, Object> nextAction(@PathVariable UUID leadId) {
        return aiService.nextBestAction(CurrentUser.require().getOrganizationId(), leadId);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('" + PermissionKeys.AI_USE + "')")
    public List<Map<String, Object>> history() {
        return aiService.history(CurrentUser.require().getOrganizationId());
    }
}
