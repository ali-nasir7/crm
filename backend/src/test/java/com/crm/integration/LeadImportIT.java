package com.crm.integration;

import com.crm.modules.importx.service.ImportService;
import com.crm.modules.leads.repo.LeadRepository;
import com.crm.modules.organization.domain.Organization;
import com.crm.modules.organization.repo.OrganizationRepository;
import com.crm.modules.identity.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Critical test #5: import handles duplicates. */
class LeadImportIT extends IntegrationTestBase {

    @Autowired ImportService importService;
    @Autowired LeadRepository leads;
    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;

    private record Ctx(UUID orgId, UUID userId) {}

    private Ctx org() {
        var admin = users.findByEmailIgnoreCase("admin@test.local").orElseThrow();
        return new Ctx(admin.getOrganizationId(), admin.getId());
    }

    @Test
    void importDeduplicatesByEmail() {
        Ctx ctx = org();
        String csv = """
            Business Name,Email,Phone,City
            Alpha Clinic,alpha@clinic.com,+971501111111,Dubai
            Beta Clinic,beta@clinic.com,+971502222222,Abu Dhabi
            Alpha Duplicate,alpha@clinic.com,+971503333333,Dubai
            Gamma Clinic,,,Sharjah
            """;
        byte[] file = csv.getBytes();

        var job = importService.upload(ctx.orgId(), ctx.userId(), "leads.csv", file);
        assertThat(job.totalRows()).isEqualTo(4);
        assertThat(job.suggestedMapping()).containsEntry("Business Name", "business_name").containsEntry("Email", "email");

        importService.submitMapping(ctx.orgId(), job.id(),
            new ImportService.MappingRequest(job.suggestedMapping(), "SKIP", Map.of()));
        importService.process(ctx.orgId(), job.id());

        var done = awaitCompleted(ctx.orgId(), job.id());
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.importedRows()).isEqualTo(3);
        assertThat(done.duplicateRows()).isEqualTo(1);
        assertThat(done.invalidRows()).isZero();

        var rows = importService.rowsView(ctx.orgId(), job.id(), "DUPLICATE", 0, 10);
        assertThat(rows.content()).hasSize(1);
        assertThat(rows.content().get(0).duplicateOfLeadId()).isNotNull();

        // second import with same emails — all duplicates now
        var job2 = importService.upload(ctx.orgId(), ctx.userId(), "leads2.csv", file);
        importService.submitMapping(ctx.orgId(), job2.id(),
            new ImportService.MappingRequest(job2.suggestedMapping(), "SKIP", Map.of()));
        importService.process(ctx.orgId(), job2.id());
        var done2 = awaitCompleted(ctx.orgId(), job2.id());
        assertThat(done2.importedRows()).isZero();
        assertThat(done2.duplicateRows()).isEqualTo(3);
    }

    @Test
    void invalidRowsAreReported() {
        Ctx ctx = org();
        String csv = """
            Business Name,Email
            Ok Clinic,ok@clinic.com
            ,not-an-email
            """;
        var job = importService.upload(ctx.orgId(), ctx.userId(), "bad.csv", file(csv));
        importService.submitMapping(ctx.orgId(), job.id(),
            new ImportService.MappingRequest(job.suggestedMapping(), "SKIP", Map.of()));
        importService.process(ctx.orgId(), job.id());
        var done = importService.get(ctx.orgId(), job.id());
        assertThat(done.importedRows()).isEqualTo(1);
        assertThat(done.invalidRows()).isEqualTo(1);
    }

    private byte[] file(String csv) { return csv.getBytes(); }

    private ImportService.ImportJobItem awaitCompleted(UUID orgId, UUID jobId) {
        for (int i = 0; i < 100; i++) {
            var job = importService.get(orgId, jobId);
            if ("COMPLETED".equals(job.status()) || "FAILED".equals(job.status())) return job;
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        throw new IllegalStateException("Import did not complete in time");
    }
}
