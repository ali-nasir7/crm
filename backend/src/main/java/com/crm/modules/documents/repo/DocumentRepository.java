package com.crm.modules.documents.repo;

import com.crm.modules.documents.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    @Query("""
        select d from Document d where d.organizationId = :orgId
        and (:leadId is null or d.leadId = :leadId)
        and (:companyId is null or d.companyId = :companyId)
        and (:dealId is null or d.dealId = :dealId)
        and (:proposalId is null or d.proposalId = :proposalId)
        and (:clientId is null or d.clientId = :clientId)
        order by d.createdAt desc""")
    List<Document> findFiltered(UUID orgId, UUID leadId, UUID companyId, UUID dealId, UUID proposalId, UUID clientId);

    @Query("select x from Document x where x.organizationId = :orgId and x.id = :id")
    Optional<Document> findInOrg(UUID orgId, UUID id);
}
