package com.crm.modules.importx.domain;

import com.crm.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Import wizard state machine: PENDING → AWAITING_MAPPING → VALIDATING → PROCESSING → COMPLETED/FAILED. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "import_jobs", indexes = @Index(name = "ix_imports_org", columnList = "organization_id, created_at"))
public class ImportJob extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_type", length = 12)
    private String fileType; // CSV, XLSX

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "valid_rows", nullable = false)
    private int validRows;

    @Column(name = "duplicate_rows", nullable = false)
    private int duplicateRows;

    @Column(name = "invalid_rows", nullable = false)
    private int invalidRows;

    @Column(name = "imported_rows", nullable = false)
    private int importedRows;

    /** PENDING, AWAITING_MAPPING, PROCESSING, COMPLETED, FAILED */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> mapping; // sheet column name -> lead field key

    /** SKIP, UPDATE_EXISTING, CREATE_ANYWAY */
    @Column(name = "duplicate_strategy", nullable = false, length = 20)
    private String duplicateStrategy = "SKIP";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", columnDefinition = "jsonb")
    private Map<String, Object> options; // defaultSourceId, defaultAssignee, tags


    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "completed_at")
    private Instant completedAt;
}
