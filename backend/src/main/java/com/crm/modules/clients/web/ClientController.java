package com.crm.modules.clients.web;

import com.crm.common.api.PageResponse;
import com.crm.modules.clients.dto.ClientDtos.*;
import com.crm.modules.clients.service.ClientService;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
@Tag(name = "Clients")
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.CLIENT_VIEW + "')")
    public PageResponse<ClientItem> list(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "25") int size) {
        return clientService.list(CurrentUser.require().getOrganizationId(), status, q, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CLIENT_VIEW + "')")
    public ClientItem get(@PathVariable UUID id) {
        return clientService.get(CurrentUser.require().getOrganizationId(), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CLIENT_UPDATE + "')")
    public ClientItem update(@PathVariable UUID id, @Valid @RequestBody UpdateClientRequest request) {
        return clientService.update(CurrentUser.require().getOrganizationId(), id, request);
    }
}
