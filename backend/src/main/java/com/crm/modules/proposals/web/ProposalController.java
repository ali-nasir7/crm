package com.crm.modules.proposals.web;

import com.crm.common.api.PageResponse;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.proposals.dto.ProposalDtos.*;
import com.crm.modules.proposals.service.ProposalService;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/proposals")
@RequiredArgsConstructor
@Tag(name = "Proposals")
public class ProposalController {

    private final ProposalService proposalService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.PROPOSAL_VIEW + "')")
    public PageResponse<ProposalItem_> list(@RequestParam(required = false) UUID leadId,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "25") int size) {
        return proposalService.list(CurrentUser.require().getOrganizationId(), leadId, status, page, size);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.PROPOSAL_CREATE + "')")
    public ProposalItem_ create(@Valid @RequestBody CreateProposalRequest request) {
        return proposalService.create(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PROPOSAL_VIEW + "')")
    public ProposalItem_ get(@PathVariable UUID id) {
        return proposalService.get(CurrentUser.require().getOrganizationId(), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PROPOSAL_UPDATE + "')")
    public ProposalItem_ update(@PathVariable UUID id, @Valid @RequestBody UpdateProposalRequest request) {
        return proposalService.update(CurrentUser.require().getOrganizationId(), id, request);
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PROPOSAL_UPDATE + "')")
    public ProposalItem_ addItem(@PathVariable UUID id, @Valid @RequestBody ItemRequest request) {
        return proposalService.addItem(CurrentUser.require().getOrganizationId(), id, request);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PROPOSAL_UPDATE + "')")
    public void removeItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        proposalService.removeItem(CurrentUser.require().getOrganizationId(), id, itemId);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PROPOSAL_UPDATE + "')")
    public ProposalItem_ changeStatus(@PathVariable UUID id, @RequestBody StatusRequest request) {
        return proposalService.changeStatus(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), id, request.status());
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PROPOSAL_SEND + "')")
    public ProposalItem_ send(@PathVariable UUID id, @RequestBody(required = false) SendRequest request) {
        // TODO / Integration Required: attach the rendered PDF to an email via the connected account.
        // The status transition + audit + activity are already handled here.
        return proposalService.markSent(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), id);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PROPOSAL_VIEW + "')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        byte[] pdf = proposalService.renderPdf(CurrentUser.require().getOrganizationId(), id);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=proposal.pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }
}
