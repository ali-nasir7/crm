package com.crm.modules.email.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "email_templates", indexes = @Index(name = "ix_templates_org", columnList = "organization_id"))
@SQLRestriction("deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class EmailTemplate extends TenantEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(columnDefinition = "text")
    private String bodyHtml;

    @Column(name = "body_text", columnDefinition = "text")
    private String bodyText;

    /** OUTREACH, FOLLOW_UP, PROPOSAL, MEETING, RE_ENGAGEMENT, OTHER */
    @Column(length = 24)
    private String category;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "archived_at")
    private Instant archivedAt;

}
