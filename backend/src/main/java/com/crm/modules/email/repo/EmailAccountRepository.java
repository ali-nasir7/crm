package com.crm.modules.email.repo;

import com.crm.modules.email.domain.EmailAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface EmailAccountRepository extends JpaRepository<EmailAccount, UUID> {

    List<EmailAccount> findByOrganizationIdAndUserIdOrderByCreatedAtAsc(UUID organizationId, UUID userId);

    @Query("select a from EmailAccount a where a.organizationId = :orgId")
    List<EmailAccount> findAllInOrg(UUID orgId);
}
