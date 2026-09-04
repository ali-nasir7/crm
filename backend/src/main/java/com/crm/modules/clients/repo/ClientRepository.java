package com.crm.modules.clients.repo;

import com.crm.modules.clients.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID>, JpaSpecificationExecutor<Client> {

    @Query("select c from Client c where c.organizationId = :orgId and c.companyId = :companyId")
    Optional<Client> findByCompany(UUID orgId, UUID companyId);
}
