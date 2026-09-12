package com.crm.modules.email.repo;

import com.crm.modules.email.domain.EmailMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, UUID>, JpaSpecificationExecutor<EmailMessage> {

    Page<EmailMessage> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Optional<EmailMessage> findByTrackingId(String trackingId);

    @Query("select e from EmailMessage e where e.leadId = :leadId order by e.createdAt desc")
    Page<EmailMessage> findByLead(UUID leadId, Pageable pageable);

    long countByOrganizationIdAndAccountIdAndStatusAndSentAtBetween(UUID orgId, UUID accountId, EmailMessage.Status status, Instant from, Instant to);

    @Query("select count(e) from EmailMessage e where e.organizationId = :orgId and e.direction = 'OUTBOUND' and e.createdAt between :from and :to")
    long countSentBetween(UUID orgId, Instant from, Instant to);

    @Query("select count(e) from EmailMessage e where e.organizationId = :orgId and e.repliedAt is not null and e.createdAt between :from and :to")
    long countRepliesBetween(UUID orgId, Instant from, Instant to);

    @Query("select count(e) from EmailMessage e where e.organizationId = :orgId and e.openedAt is not null and e.createdAt between :from and :to")
    long countOpenedBetween(UUID orgId, Instant from, Instant to);

    @Query("select e.userId, count(e) from EmailMessage e where e.organizationId = :orgId and e.direction = 'OUTBOUND' " +
           "and e.createdAt between :from and :to group by e.userId")
    List<Object[]> countByUserBetween(UUID orgId, Instant from, Instant to);
}
