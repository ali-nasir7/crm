package com.crm.modules.importx.domain;

import com.crm.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

/** One spreadsheet row: raw values, validation status, errors and the created lead (if imported). */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "import_rows", indexes = {
    @Index(name = "ix_irows_job_status", columnList = "job_id, status"),
    @Index(name = "ix_irows_job_row", columnList = "job_id, row_number")})
public class ImportRow extends BaseEntity {

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> raw;

    /** PENDING, VALID, INVALID, DUPLICATE, IMPORTED, FAILED */
    @Column(nullable = false, length = 12)
    private String status = "PENDING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> errors;

    @Column(name = "duplicate_of_lead_id")
    private UUID duplicateOfLeadId;

    @Column(name = "imported_lead_id")
    private UUID importedLeadId;
}
