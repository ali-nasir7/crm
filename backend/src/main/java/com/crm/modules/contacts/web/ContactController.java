package com.crm.modules.contacts.web;

import com.crm.common.api.PageResponse;
import com.crm.modules.contacts.dto.ContactDtos.*;
import com.crm.modules.contacts.service.ContactService;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
@Tag(name = "Contacts")
public class ContactController {

    private final ContactService contactService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.CONTACT_VIEW + "')")
    public PageResponse<ContactItem> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) UUID companyId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "25") int size) {
        return contactService.list(CurrentUser.require().getOrganizationId(), q, companyId, page, size);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.CONTACT_CREATE + "')")
    public ContactItem create(@Valid @RequestBody CreateContactRequest request) {
        return contactService.create(CurrentUser.require().getOrganizationId(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CONTACT_VIEW + "')")
    public ContactItem get(@PathVariable UUID id) {
        return contactService.get(CurrentUser.require().getOrganizationId(), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CONTACT_UPDATE + "')")
    public ContactItem update(@PathVariable UUID id, @Valid @RequestBody UpdateContactRequest request) {
        return contactService.update(CurrentUser.require().getOrganizationId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CONTACT_DELETE + "')")
    public void delete(@PathVariable UUID id) {
        contactService.delete(CurrentUser.require().getOrganizationId(), id);
    }
}
