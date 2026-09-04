package com.crm.modules.meetings.web;

import com.crm.common.api.PageResponse;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.meetings.dto.MeetingDtos.*;
import com.crm.modules.meetings.service.MeetingService;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
@Tag(name = "Meetings")
public class MeetingController {

    private final MeetingService meetingService;

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.MEETING_CREATE + "')")
    public MeetingItem create(@Valid @RequestBody CreateMeetingRequest request) {
        return meetingService.create(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.MEETING_VIEW + "')")
    public PageResponse<MeetingItem> list(@RequestParam(required = false) UUID leadId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "25") int size) {
        return meetingService.list(CurrentUser.require().getOrganizationId(), leadId, page, size);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.MEETING_UPDATE + "')")
    public MeetingItem update(@PathVariable UUID id, @Valid @RequestBody UpdateMeetingRequest request) {
        return meetingService.update(CurrentUser.require().getOrganizationId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.MEETING_DELETE + "')")
    public void delete(@PathVariable UUID id) {
        meetingService.delete(CurrentUser.require().getOrganizationId(), id);
    }
}
