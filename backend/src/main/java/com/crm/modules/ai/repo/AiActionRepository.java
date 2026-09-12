package com.crm.modules.ai.repo;

import com.crm.modules.ai.domain.AiAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiActionRepository extends JpaRepository<AiAction, UUID> {
    List<AiAction> findTop50ByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
