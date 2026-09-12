package com.crm.modules.bulk.repo;

import com.crm.modules.bulk.domain.BulkJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface BulkJobRepository extends JpaRepository<BulkJob, UUID> {
    Page<BulkJob> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);
    @Query("select b from BulkJob b where b.organizationId = :orgId and b.id = :id")
    Optional<BulkJob> findInOrg(UUID orgId, UUID id);
}
