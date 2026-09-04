package com.crm.unit;

import com.crm.config.CrmProperties;
import com.crm.security.JwtService;
import com.crm.security.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwt = new JwtService(new CrmProperties(
        new CrmProperties.App("Nexus", "/api/v1", "test-secret-test-secret-test-secret-test-secret-test-secret-test-secret-test-secret-0123456789",
            15, 30, "http://localhost:5173", "enc-key-enc-key-enc-key-enc-key-enc-key", "./data", "http://localhost:5173"),
        new CrmProperties.Seed(false, "a@b.c", "x", "Org"),
        new CrmProperties.Mail("localhost", 1025, "", "", "noreply@test"),
        new CrmProperties.Ai("", "", "")));

    private UserPrincipal principal() {
        return new UserPrincipal(UUID.randomUUID(), UUID.randomUUID(), "user@test.io", "Test User", "hash",
            true, false, Set.of("SALES_REP"), Set.of("LEAD_VIEW", "CALL_CREATE"));
    }

    @Test
    void roundTripsPrincipalClaims() {
        UserPrincipal p = principal();
        String token = jwt.createAccessToken(p);
        UserPrincipal parsed = jwt.parse(token);
        assertThat(parsed).isNotNull();
        assertThat(parsed.getId()).isEqualTo(p.getId());
        assertThat(parsed.getOrganizationId()).isEqualTo(p.getOrganizationId());
        assertThat(parsed.getUsername()).isEqualTo("user@test.io");
        assertThat(parsed.getPermissions()).containsExactlyInAnyOrder("LEAD_VIEW", "CALL_CREATE");
        assertThat(parsed.getRoles()).containsExactly("SALES_REP");
        assertThat(parsed.hasPermission("LEAD_VIEW")).isTrue();
        assertThat(parsed.hasPermission("LEAD_DELETE")).isFalse();
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwt.createAccessToken(principal()) + "x";
        assertThat(jwt.parse(token)).isNull();
        assertThat(jwt.parse("garbage")).isNull();
    }
}
