package com.crm.modules.identity.repo;

import com.crm.modules.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmailIgnoreCase(String email);
    List<User> findByOrganizationId(UUID organizationId);
    boolean existsByOrganizationIdAndId(UUID organizationId, UUID id);
    long countByOrganizationId(UUID organizationId);
}
