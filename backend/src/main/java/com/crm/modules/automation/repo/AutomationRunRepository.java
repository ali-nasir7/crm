package com.crm.modules.automation.repo;

import com.crm.modules.automation.domain.AutomationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AutomationRunRepository extends JpaRepository<AutomationRun, UUID> {
    List<AutomationRun> findByRuleIdOrderByCreatedAtDesc(UUID ruleId, org.springframework.data.domain.Pageable pageable);
}
