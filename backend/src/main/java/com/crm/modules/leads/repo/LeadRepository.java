package com.crm.modules.leads.repo;

import com.crm.modules.leads.domain.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

    @Query("select l from Lead l where l.organizationId = :orgId and l.id = :id")
    Optional<Lead> findInOrg(UUID orgId, UUID id);

    @Query("select count(l) from Lead l where l.organizationId = :orgId and l.assignedUserId = :userId")
    long countByAssignee(UUID orgId, UUID userId);

    @Modifying
    @Query("update Lead l set l.score = :score where l.id = :id")
    void updateScore(UUID id, int score);

    /** Per-day lead inflow for dashboard charts (grouped in the DB, not in memory). */
    @Query(value = "select to_char(created_at, 'YYYY-MM-DD') as day, count(*) from leads " +
                   "where organization_id = :orgId and created_at >= :from group by day order by day", nativeQuery = true)
    List<Object[]> countPerDay(UUID orgId, Instant from);
}
