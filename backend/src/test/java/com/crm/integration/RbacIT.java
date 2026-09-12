package com.crm.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Critical tests #2/#4/#8/#9: permission enforcement over HTTP. */
class RbacIT extends IntegrationTestBase {

    @Autowired TestRestTemplate http;

    @SuppressWarnings("unchecked")
    private String tokenFor(String email, String password) {
        ResponseEntity<Map> res = http.postForEntity("/api/v1/auth/login", Map.of("email", email, "password", password), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) res.getBody().get("accessToken");
    }

    private HttpHeaders auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void adminCanManageUsers_butRepCannot() {
        String admin = tokenFor("admin@test.local", "Admin123!");
        // create a rep
        var create = http.exchange("/api/v1/users", HttpMethod.POST,
            new HttpEntity<>(Map.of("email", "rep2@test.local", "password", "Password123", "firstName", "Re", "lastName", "Pa",
                "roleKeys", java.util.List.of("SALES_REP")), auth(admin)), Map.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.OK);

        String rep = tokenFor("rep2@test.local", "Password123!");
        // rep lacks USER_CREATE
        var denied = http.exchange("/api/v1/users", HttpMethod.POST,
            new HttpEntity<>(Map.of("email", "x@test.local", "password", "Password123", "firstName", "X", "lastName", "Y"), auth(rep)), String.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // admin can list users
        var list = http.exchange("/api/v1/users", HttpMethod.GET, new HttpEntity<>(auth(admin)), String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void repWithoutEmailSendPermissionIsBlocked() {
        String admin = tokenFor("admin@test.local", "Admin123!");
        var create = http.exchange("/api/v1/users", HttpMethod.POST,
            new HttpEntity<>(Map.of("email", "viewer@test.local", "password", "Password123", "firstName", "V", "lastName", "R",
                "roleKeys", java.util.List.of("VIEWER")), auth(admin)), Map.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.OK);
        String viewer = tokenFor("viewer@test.local", "Password123!");

        var res = http.exchange("/api/v1/leads/00000000-0000-0000-0000-000000000000/emails", HttpMethod.POST,
            new HttpEntity<>(Map.of("accountId", java.util.UUID.randomUUID(), "subject", "s"), auth(viewer)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void viewerCanReadLeadsButNotCreate() {
        String admin = tokenFor("admin@test.local", "Admin123!");
        var create = http.exchange("/api/v1/users", HttpMethod.POST,
            new HttpEntity<>(Map.of("email", "viewer2@test.local", "password", "Password123", "firstName", "V", "lastName", "R",
                "roleKeys", java.util.List.of("VIEWER")), auth(admin)), Map.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.OK);
        String viewer = tokenFor("viewer2@test.local", "Password123!");

        assertThat(http.exchange("/api/v1/leads", HttpMethod.GET, new HttpEntity<>(auth(viewer)), String.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        var createLead = http.exchange("/api/v1/leads", HttpMethod.POST,
            new HttpEntity<>(Map.of("businessName", "Nope Clinic"), auth(viewer)), String.class);
        assertThat(createLead.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
