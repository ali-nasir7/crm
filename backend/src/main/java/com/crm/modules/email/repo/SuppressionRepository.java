package com.crm.modules.email.repo;

import com.crm.modules.email.domain.Suppression;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SuppressionRepository extends JpaRepository<Suppression, UUID> {

    @Query("select s from Suppression s where s.organizationId = :orgId and lower(s.email) = lower(:email)")
    Optional<Suppression> findInOrg(UUID orgId, String email);

    @Query("select s from Suppression s where s.organizationId = :orgId order by s.createdAt desc")
    List<Suppression> findAllInOrg(UUID orgId);
}
