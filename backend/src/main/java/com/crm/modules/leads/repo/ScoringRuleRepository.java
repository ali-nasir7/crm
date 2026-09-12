package com.crm.modules.leads.repo;

import com.crm.modules.leads.domain.ScoringRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScoringRuleRepository extends JpaRepository<ScoringRule, UUID> {
    List<ScoringRule> findByOrganizationIdAndActiveTrueOrderByPositionAsc(UUID organizationId);
    List<ScoringRule> findByOrganizationIdOrderByPositionAsc(UUID organizationId);
}
