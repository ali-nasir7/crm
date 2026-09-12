package com.crm.modules.meetings.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Calendar-ready meeting. Google/Microsoft calendar sync is an integration TODO (provider interface). */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "meetings", indexes = {
    @Index(name = "ix_meetings_org_start", columnList = "organization_id, start_at"),
    @Index(name = "ix_meetings_lead", columnList = "lead_id")})
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class Meeting extends TenantEntity {

    @Column(nullable = false, length = 160)
    private String title;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "participants", columnDefinition = "jsonb")
    private List<String> participants; // names/emails of external attendees

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 30;

    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    @Column(name = "location", length = 255)
    private String location;

    @Column(columnDefinition = "text")
    private String notes;

    /** SCHEDULED, COMPLETED, CANCELLED, NO_SHOW */
    @Column(nullable = false, length = 12)
    private String status = "SCHEDULED";
}
