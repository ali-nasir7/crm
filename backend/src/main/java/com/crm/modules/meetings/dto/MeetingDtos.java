package com.crm.modules.meetings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MeetingDtos {
    private MeetingDtos() {}

    public record CreateMeetingRequest(@NotBlank @Size(max = 160) String title, UUID leadId, UUID companyId,
                                       List<String> participants, Instant startAt, Integer durationMinutes,
                                       String meetingLink, String location, String notes) {}

    public record UpdateMeetingRequest(String title, Instant startAt, Integer durationMinutes,
                                       String meetingLink, String location, String notes, String status) {}

    public record MeetingItem(UUID id, String title, UUID leadId, String businessName, UUID companyId,
                              UUID ownerId, String ownerName, List<String> participants, Instant startAt,
                              int durationMinutes, String meetingLink, String location, String notes,
                              String status, Instant createdAt) {}
}
