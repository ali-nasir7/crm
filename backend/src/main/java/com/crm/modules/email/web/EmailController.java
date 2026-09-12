package com.crm.modules.email.web;

import com.crm.common.api.PageResponse;
import com.crm.modules.email.dto.EmailDtos.*;
import com.crm.modules.email.service.EmailService;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Emails")
public class EmailController {

    private final EmailService emailService;

    @PostMapping("/leads/{leadId}/emails")
    @PreAuthorize("hasAuthority('" + PermissionKeys.EMAIL_SEND + "')")
    public EmailItem sendToLead(@PathVariable UUID leadId, @Valid @RequestBody SendEmailRequest request) {
        return emailService.sendToLead(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), leadId, request);
    }

    @GetMapping("/emails")
    @PreAuthorize("hasAuthority('" + PermissionKeys.EMAIL_VIEW + "')")
    public PageResponse<EmailItem> list(@RequestParam(required = false) UUID leadId,
                                        @RequestParam(required = false) String direction,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "25") int size) {
        return emailService.list(CurrentUser.require().getOrganizationId(), leadId, direction, status, page, size);
    }
}
