package com.crm.modules.deals.repo;

import com.crm.modules.deals.domain.Deal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DealRepository extends JpaRepository<Deal, UUID>, JpaSpecificationExecutor<Deal> {

    @Query("select d from Deal d where d.organizationId = :orgId and d.id = :id")
    Optional<Deal> findInOrg(UUID orgId, UUID id);

    Page<Deal> findByOrganizationIdOrderByCreatedAtDesc(UUID orgId, Pageable pageable);

    long countByOrganizationIdAndStatus(UUID orgId, String status);

    @Query("select coalesce(sum(d.amount), 0) from Deal d where d.organizationId = :orgId and d.status = :status")
    BigDecimal sumAmountByStatus(UUID orgId, String status);

    @Query("select coalesce(sum(d.amount), 0) from Deal d where d.organizationId = :orgId and d.status = 'OPEN'")
    BigDecimal sumOpenAmount(UUID orgId);

    @Query("select coalesce(sum(d.amount * d.probability / 100.0), 0) from Deal d where d.organizationId = :orgId and d.status = 'OPEN'")
    BigDecimal sumWeightedAmount(UUID orgId);

    @Query("select coalesce(sum(d.amount), 0) from Deal d where d.organizationId = :orgId and d.status = 'WON' and d.closedAt between :from and :to")
    BigDecimal sumWonBetween(UUID orgId, Instant from, Instant to);

    @Query("select coalesce(sum(d.amount), 0) from Deal d where d.organizationId = :orgId and d.status = 'LOST' and d.closedAt between :from and :to")
    BigDecimal sumLostBetween(UUID orgId, Instant from, Instant to);

    long countByOrganizationIdAndStatusAndClosedAtBetween(UUID orgId, String status, Instant from, Instant to);

    @Query("select d.ownerId, count(d), coalesce(sum(case when d.status = 'WON' then d.amount else 0 end), 0) from Deal d " +
           "where d.organizationId = :orgId and d.createdAt between :from and :to group by d.ownerId")
    List<Object[]> ownerStats(UUID orgId, Instant from, Instant to);

    @Query("select coalesce(sum(d.amount), 0) from Deal d where d.organizationId = :orgId and d.clientId = :clientId and d.status = 'WON'")
    BigDecimal sumWonForClient(UUID orgId, UUID clientId);

    /** Open deals grouped by stage: [stageId, count, sum(amount)] for the pipeline chart. */
    @Query("select d.stageId, count(d), coalesce(sum(d.amount), 0) from Deal d " +
           "where d.organizationId = :orgId and d.status = 'OPEN' group by d.stageId")
    List<Object[]> openByStage(UUID orgId);
}
