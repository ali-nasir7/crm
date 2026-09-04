package com.crm.modules.calling.repo;

import com.crm.modules.calling.domain.CallingDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CallingDeviceRepository extends JpaRepository<CallingDevice, UUID> {

    List<CallingDevice> findByOrganizationIdAndUserIdOrderByCreatedAtAsc(UUID organizationId, UUID userId);

    @Query("select d from CallingDevice d where d.organizationId = :orgId and d.id = :id")
    Optional<CallingDevice> findInOrg(UUID orgId, UUID id);

    @Query("select d from CallingDevice d where d.organizationId = :orgId and d.userId = :userId and d.id = :id")
    Optional<CallingDevice> findOwned(UUID orgId, UUID userId, UUID id);

    /** Same derived pattern as PipelineRepository.isDefault, proven at boot. */
    Optional<CallingDevice> findFirstByUserIdAndIsDefaultTrue(UUID userId);

    List<CallingDevice> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);
}
