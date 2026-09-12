package com.crm.modules.companies.repo;

import com.crm.modules.companies.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID>, JpaSpecificationExecutor<Company> {

    @Query("select c from Company c where c.organizationId = :orgId and lower(c.website) = lower(:website)")
    List<Company> findByWebsite(UUID orgId, String website);

    @Query("select c from Company c where c.organizationId = :orgId and lower(c.name) = lower(:name)")
    List<Company> findByNameExact(UUID orgId, String name);

    @Query("select c from Company c where c.organizationId = :orgId and c.id in :ids")
    List<Company> findAllInOrg(UUID orgId, List<UUID> ids);
}
