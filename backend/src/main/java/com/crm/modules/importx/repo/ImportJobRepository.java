package com.crm.modules.importx.repo;

import com.crm.modules.importx.domain.ImportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {
    Page<ImportJob> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);
    @Query("select x from ImportJob x where x.organizationId = :orgId and x.id = :id")
    Optional<ImportJob> findInOrg(UUID orgId, UUID id);
}
