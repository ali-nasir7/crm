package com.crm.modules.email.web;

import com.crm.common.api.PageResponse;
import com.crm.modules.email.domain.Suppression;
import com.crm.modules.email.service.SuppressionService;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suppressions")
@RequiredArgsConstructor
@Tag(name = "Suppression List")
public class SuppressionController {

    private final SuppressionService suppressionService;

    public record AddSuppressionRequest(@NotBlank String email, String reason, String note) {}

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.EMAIL_VIEW + "')")
    public PageResponse<Suppression> list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size) {
        return suppressionService.list(CurrentUser.require().getOrganizationId(), page, size);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.EMAIL_SEND + "')")
    public Suppression add(@RequestBody AddSuppressionRequest request) {
        Suppression.Reason reason;
        try {
            reason = request.reason() == null ? Suppression.Reason.MANUAL : Suppression.Reason.valueOf(request.reason().toUpperCase());
        } catch (IllegalArgumentException e) {
            reason = Suppression.Reason.MANUAL;
        }
        return suppressionService.add(CurrentUser.require().getOrganizationId(), request.email(), reason, request.note());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.EMAIL_SEND + "')")
    public void remove(@PathVariable UUID id) {
        suppressionService.remove(CurrentUser.require().getOrganizationId(), id);
    }
}
