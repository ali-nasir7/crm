package com.crm.modules.bulk.domain;

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

/** Async bulk operation with observable progress (never blocks the HTTP request). */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "bulk_jobs", indexes = @Index(name = "ix_bulk_org", columnList = "organization_id, created_at"))
public class BulkJob extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** ASSIGN, STAGE, STATUS, ADD_TAG, REMOVE_TAG, DELETE */
    @Column(name = "job_type", nullable = false, length = 16)
    private String jobType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> params;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "processed_count", nullable = false)
    private int processedCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    /** PENDING, RUNNING, COMPLETED, FAILED */
    @Column(nullable = false, length = 12)
    private String status = "PENDING";

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "completed_at")
    private Instant completedAt;
}
