package com.crm.integration;

import com.crm.modules.audit.repo.AuditLogRepository;
import com.crm.modules.identity.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Critical test #10: audit entries are written for key actions. */
class AuditLogIT extends IntegrationTestBase {

    @Autowired com.crm.modules.audit.service.AuditService audit;
    @Autowired AuditLogRepository logs;
    @Autowired UserRepository users;

    @Test
    void auditWriteIsQueryable() {
        var admin = users.findByEmailIgnoreCase("admin@test.local").orElseThrow();
        audit.log("TEST_ACTION", "LEAD", null, "Test label", Map.of("before", 1), Map.of("after", 2));
        var found = logs.findAll().stream()
            .filter(l -> "TEST_ACTION".equals(l.getAction())).findFirst();
        assertThat(found).isPresent();
        assertThat(found.get().getNewValues()).containsEntry("after", 2);
    }
}
