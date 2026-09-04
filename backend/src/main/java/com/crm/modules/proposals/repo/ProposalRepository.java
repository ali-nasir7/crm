package com.crm.modules.proposals.repo;

import com.crm.modules.proposals.domain.Proposal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ProposalRepository extends JpaRepository<Proposal, UUID>, JpaSpecificationExecutor<Proposal> {
    Page<Proposal> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);
}
