package com.crm.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Login → me → refresh rotation → logout against the real stack (seeded admin). */
class AuthFlowIT extends IntegrationTestBase {

    @Autowired TestRestTemplate http;

    @SuppressWarnings("unchecked")
    private Map<String, Object> login() {
        ResponseEntity<Map> res = http.postForEntity("/api/v1/auth/login",
            Map.of("email", "admin@test.local", "password", "Admin123!"), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody();
    }

    @Test
    void loginMeRefreshLogout() {
        var tokens = login();
        String access = (String) tokens.get("accessToken");
        String refresh = (String) tokens.get("refreshToken");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(access);

        ResponseEntity<Map> me = http.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) me.getBody().get("email")).isEqualTo("admin@test.local");

        // refresh rotates: old token must not work twice
        ResponseEntity<Map> refreshed = http.postForEntity("/api/v1/auth/refresh", Map.of("refreshToken", refresh), Map.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String refresh2 = (String) refreshed.getBody().get("refreshToken");
        assertThat(refresh2).isNotEqualTo(refresh);

        ResponseEntity<String> reused = http.postForEntity("/api/v1/auth/refresh", Map.of("refreshToken", refresh), String.class);
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // logout invalidates the current refresh token
        HttpHeaders h2 = new HttpHeaders();
        h2.setBearerAuth((String) refreshed.getBody().get("accessToken"));
        http.exchange("/api/v1/auth/logout", HttpMethod.POST, new HttpEntity<>(Map.of("refreshToken", refresh2), h2), Map.class);
        ResponseEntity<String> afterLogout = http.postForEntity("/api/v1/auth/refresh", Map.of("refreshToken", refresh2), String.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        ResponseEntity<String> res = http.getForEntity("/api/v1/leads", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
