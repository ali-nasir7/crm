package com.crm.modules.pipeline.repo;

import com.crm.modules.pipeline.domain.Pipeline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineRepository extends JpaRepository<Pipeline, UUID> {
    List<Pipeline> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);
    Optional<Pipeline> findFirstByOrganizationIdAndIsDefaultTrue(UUID organizationId);
}
