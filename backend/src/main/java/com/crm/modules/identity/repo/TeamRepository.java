package com.crm.modules.identity.repo;

import com.crm.modules.identity.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID>, JpaSpecificationExecutor<Team> {
    List<Team> findByOrganizationIdOrderByNameAsc(UUID organizationId);
}
