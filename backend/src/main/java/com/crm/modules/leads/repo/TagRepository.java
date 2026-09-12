package com.crm.modules.leads.repo;

import com.crm.modules.leads.domain.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findByOrganizationIdOrderByNameAsc(UUID organizationId);

    /** Tenant-scoped fetch used by update/delete. @SQLRestriction excludes soft-deleted rows. */
    @Query("select t from Tag t where t.organizationId = :orgId and t.id = :id")
    Optional<Tag> findInOrg(UUID orgId, UUID id);

    /**
     * Name-collision check that also sees SOFT-DELETED rows (native SQL bypasses
     * @SQLRestriction). Needed because uk_tags_org_name covers deleted rows too.
     */
    @Query(value = "select * from tags where organization_id = :orgId and lower(name) = lower(:name) limit 1", nativeQuery = true)
    Optional<Tag> findAnyByNameIncludingDeleted(UUID orgId, String name);
}
