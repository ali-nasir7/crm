package com.crm.modules.email.domain;

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

/** Every message sent or received through the CRM. Mirrored into the lead timeline as an activity. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "emails", indexes = {
    @Index(name = "ix_emails_org_created", columnList = "organization_id, created_at"),
    @Index(name = "ix_emails_lead", columnList = "lead_id"),
    @Index(name = "ix_emails_campaign", columnList = "campaign_id")})
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class EmailMessage extends TenantEntity {

    public enum Direction { OUTBOUND, INBOUND }
    public enum Status { QUEUED, SENT, FAILED, BOUNCED }

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "user_id")
    private UUID userId; // owner of the sending account (reporting)

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Direction direction = Direction.OUTBOUND;

    @Column(name = "from_email", nullable = false, length = 255)
    private String fromEmail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "to_emails", columnDefinition = "jsonb", nullable = false)
    private List<String> toEmails;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cc_emails", columnDefinition = "jsonb")
    private List<String> ccEmails;

    @Column(length = 255)
    private String subject;

    @Column(name = "body_html", columnDefinition = "text")
    private String bodyHtml;

    @Column(name = "body_text", columnDefinition = "text")
    private String bodyText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Status status = Status.QUEUED;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "tracking_id", nullable = false, unique = true, length = 36)
    private String trackingId;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "open_count", nullable = false)
    private int openCount;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @Column(name = "bounced_at")
    private Instant bouncedAt;
}
