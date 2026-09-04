package com.crm.modules.identity.repo;

import com.crm.modules.identity.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    List<Permission> findAllByOrderByCategoryAscKeyAsc();
    List<Permission> findByKeyIn(List<String> keys);
}
