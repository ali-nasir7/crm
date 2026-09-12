package com.crm.modules.activity.repo;

import com.crm.modules.activity.domain.Activity;
import com.crm.modules.activity.domain.ActivityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID>, JpaSpecificationExecutor<Activity> {

    Page<Activity> findByOrganizationIdAndLeadIdOrderByOccurredAtDesc(UUID organizationId, UUID leadId, Pageable pageable);

    long countByOrganizationIdAndLeadIdAndType(UUID organizationId, UUID leadId, ActivityType type);

    @Query("select a.type, count(a) from Activity a where a.organizationId = :orgId and a.occurredAt >= :from and a.actorId = :userId group by a.type")
    java.util.List<Object[]> countByTypeSince(UUID orgId, UUID userId, java.time.Instant from);
}
