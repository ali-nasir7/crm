package com.crm.modules.documents.web;

import com.crm.modules.documents.domain.Document;
import com.crm.modules.documents.service.DocumentService;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.DOCUMENT_CREATE + "')")
    public Document upload(@RequestParam("file") MultipartFile file,
                           @RequestParam(required = false) UUID leadId,
                           @RequestParam(required = false) UUID companyId,
                           @RequestParam(required = false) UUID dealId,
                           @RequestParam(required = false) UUID proposalId,
                           @RequestParam(required = false) UUID clientId) {
        return documentService.upload(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(),
            file, leadId, companyId, dealId, proposalId, clientId);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.DOCUMENT_VIEW + "')")
    public List<Document> list(@RequestParam(required = false) UUID leadId,
                               @RequestParam(required = false) UUID companyId,
                               @RequestParam(required = false) UUID dealId,
                               @RequestParam(required = false) UUID proposalId,
                               @RequestParam(required = false) UUID clientId) {
        return documentService.list(CurrentUser.require().getOrganizationId(), leadId, companyId, dealId, proposalId, clientId);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('" + PermissionKeys.DOCUMENT_VIEW + "')")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        var dl = documentService.download(CurrentUser.require().getOrganizationId(), id);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dl.document().getFileName().replaceAll("[^a-zA-Z0-9._-]", "_") + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(dl.content());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.DOCUMENT_DELETE + "')")
    public void delete(@PathVariable UUID id) {
        documentService.delete(CurrentUser.require().getOrganizationId(), id);
    }
}
