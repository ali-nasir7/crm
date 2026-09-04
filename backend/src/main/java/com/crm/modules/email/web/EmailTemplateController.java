package com.crm.modules.email.web;

import com.crm.modules.email.dto.EmailDtos.*;
import com.crm.modules.email.service.EmailService;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/email-templates")
@RequiredArgsConstructor
@Tag(name = "Email Templates")
public class EmailTemplateController {

    private final EmailService emailService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.TEMPLATE_VIEW + "')")
    public List<TemplateItem> list() {
        return emailService.listTemplates(CurrentUser.require().getOrganizationId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.TEMPLATE_CREATE + "')")
    public TemplateItem create(@Valid @RequestBody TemplateRequest request) {
        return emailService.createTemplate(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.TEMPLATE_UPDATE + "')")
    public TemplateItem update(@PathVariable UUID id, @Valid @RequestBody TemplateRequest request) {
        return emailService.updateTemplate(CurrentUser.require().getOrganizationId(), id, request);
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAuthority('" + PermissionKeys.TEMPLATE_CREATE + "')")
    public TemplateItem duplicate(@PathVariable UUID id) {
        return emailService.duplicateTemplate(CurrentUser.require().getOrganizationId(), id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.TEMPLATE_DELETE + "')")
    public void archive(@PathVariable UUID id) {
        emailService.archiveTemplate(CurrentUser.require().getOrganizationId(), id);
    }

    @PostMapping("/{id}/render")
    @PreAuthorize("hasAuthority('" + PermissionKeys.TEMPLATE_VIEW + "')")
    public EmailItem render(@PathVariable UUID id, @RequestParam UUID leadId) {
        return emailService.renderTemplate(CurrentUser.require().getOrganizationId(), id, leadId);
    }
}
