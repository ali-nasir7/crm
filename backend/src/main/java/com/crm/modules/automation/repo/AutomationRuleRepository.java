package com.crm.modules.automation.repo;

import com.crm.modules.automation.domain.AutomationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {
    List<AutomationRule> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    @Query("select r from AutomationRule r where r.active = true and r.trigger in ('NO_REPLY_AFTER','TASK_OVERDUE')")
    List<AutomationRule> findScannerRules();
}
