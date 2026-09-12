package com.crm.modules.documents.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Attachment linked to any business entity (lead, company, deal, proposal, client). */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "documents", indexes = {
    @Index(name = "ix_docs_org", columnList = "organization_id, created_at"),
    @Index(name = "ix_docs_lead", columnList = "lead_id"),
    @Index(name = "ix_docs_client", columnList = "client_id")})
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class Document extends TenantEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "deal_id")
    private UUID dealId;

    @Column(name = "proposal_id")
    private UUID proposalId;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;
}
