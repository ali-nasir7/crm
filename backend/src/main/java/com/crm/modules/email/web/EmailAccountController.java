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
@RequestMapping("/api/v1/email-accounts")
@RequiredArgsConstructor
@Tag(name = "Email Accounts")
public class EmailAccountController {

    private final EmailService emailService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.EMAIL_ACCOUNT_VIEW + "')")
    public List<AccountItem> list(@RequestParam(defaultValue = "true") boolean mine) {
        return emailService.listAccounts(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), mine);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.EMAIL_ACCOUNT_CREATE + "')")
    public AccountItem create(@Valid @RequestBody CreateAccountRequest request) {
        return emailService.createAccount(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.EMAIL_ACCOUNT_DELETE + "')")
    public void delete(@PathVariable UUID id) {
        emailService.deleteAccount(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), id);
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAuthority('" + PermissionKeys.EMAIL_ACCOUNT_UPDATE + "')")
    public AccountItem verify(@PathVariable UUID id) {
        return emailService.verifyAccount(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), id);
    }
}
