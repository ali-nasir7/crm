package com.crm.modules.importx.web;

import com.crm.common.api.PageResponse;
import com.crm.common.api.ApiException;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.importx.service.ImportService.MappingRequest;
import com.crm.modules.importx.service.ImportService;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
@Tag(name = "Lead Import")
public class ImportController {

    private final ImportService importService;

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_IMPORT + "')")
    public ImportService.ImportJobItem upload(@RequestParam("file") MultipartFile file) {
        validateFile(file);
        // No @RequestBody here: multipart + JSON body on the same request is fragile in Spring.
        // Import options (assignee, tags, strategy) belong to the mapping phase anyway.
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw ApiException.business("Could not read the uploaded file (" + e.getClass().getSimpleName()
                + "). Try saving it as a plain .csv and uploading again.");
        }
        return importService.upload(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(),
            file.getOriginalFilename() == null ? "upload.csv" : file.getOriginalFilename(), content);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.IMPORT_VIEW + "')")
    public PageResponse<ImportService.ImportJobItem> history(@RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return importService.history(CurrentUser.require().getOrganizationId(), page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.IMPORT_VIEW + "')")
    public ImportService.ImportJobItem get(@PathVariable UUID id) {
        return importService.get(CurrentUser.require().getOrganizationId(), id);
    }

    @GetMapping("/{id}/rows")
    @PreAuthorize("hasAuthority('" + PermissionKeys.IMPORT_VIEW + "')")
    public PageResponse<ImportService.ImportRowItem> rows(@PathVariable UUID id,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "50") int size) {
        return importService.rowsView(CurrentUser.require().getOrganizationId(), id, status, page, size);
    }

    /** Submit column mapping + options; kicks off async processing. */
    @PutMapping("/{id}/mapping")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_IMPORT + "')")
    public Map<String, Object> submitMapping(@PathVariable UUID id, @RequestBody MappingRequest request) {
        UUID orgId = CurrentUser.require().getOrganizationId();
        importService.submitMapping(orgId, id, request);
        importService.process(orgId, id); // @Async
        return Map.of("status", "PROCESSING");
    }

    @GetMapping("/{id}/errors.csv")
    @PreAuthorize("hasAuthority('" + PermissionKeys.IMPORT_VIEW + "')")
    public ResponseEntity<byte[]> errorReport(@PathVariable UUID id) {
        String csv = importService.errorReport(CurrentUser.require().getOrganizationId(), id);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=import-errors.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv.getBytes());
    }

    @GetMapping("/fields")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LEAD_IMPORT + "')")
    public List<Map<String, String>> targetFields() {
        return ImportService.TARGET_FIELDS.stream().map(f -> Map.of("key", f[0], "label", f[1])).toList();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw com.crm.common.api.ApiException.badRequest("File is required");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".csv") && !name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw com.crm.common.api.ApiException.badRequest("Only .csv and .xlsx files are supported");
        }
    }
}
