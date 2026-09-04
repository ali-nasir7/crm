package com.crm.modules.identity.repo;

import com.crm.modules.identity.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    List<Role> findByOrganizationIdOrderByNameAsc(UUID organizationId);
    Optional<Role> findByOrganizationIdAndKey(UUID organizationId, String key);
    boolean existsByOrganizationIdAndKey(UUID organizationId, String key);
}
