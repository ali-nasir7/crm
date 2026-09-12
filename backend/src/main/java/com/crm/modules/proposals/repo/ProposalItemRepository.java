package com.crm.modules.proposals.repo;

import com.crm.modules.proposals.domain.ProposalItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProposalItemRepository extends JpaRepository<ProposalItem, UUID> {
    List<ProposalItem> findByProposalIdOrderByPositionAsc(UUID proposalId);
    void deleteByProposalId(UUID proposalId);
}
