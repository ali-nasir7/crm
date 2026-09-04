package com.crm.modules.leads.web;

import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.leads.domain.Tag;
import com.crm.modules.leads.service.TagService;
import com.crm.security.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "Tags")
public class TagController {

    private final TagService tags;

    public record TagRequest(@NotBlank @Size(max = 48) String name, @Size(max = 9) String color) {}

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.TAG_VIEW + "')")
    public List<Tag> list() {
        return tags.list(CurrentUser.require().getOrganizationId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.TAG_UPDATE + "')")
    public Tag create(@RequestBody TagRequest request) {
        return tags.create(CurrentUser.require().getOrganizationId(), request.name(), request.color());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.TAG_UPDATE + "')")
    public Tag update(@PathVariable UUID id, @RequestBody TagRequest request) {
        return tags.update(CurrentUser.require().getOrganizationId(), id, request.name(), request.color());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.TAG_UPDATE + "')")
    public void delete(@PathVariable UUID id) {
        tags.delete(CurrentUser.require().getOrganizationId(), id);
    }
}
