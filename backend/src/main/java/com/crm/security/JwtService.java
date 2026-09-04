package com.crm.security;

import com.crm.config.CrmProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessMinutes;

    public JwtService(CrmProperties props) {
        this.key = Keys.hmacShaKeyFor(props.app().jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.accessMinutes = props.app().jwtAccessMinutes();
    }

    public String createAccessToken(UserPrincipal user) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("org", user.getOrganizationId().toString())
            .claim("email", user.getUsername())
            .claim("name", user.getDisplayName())
            .claim("roles", user.getRoles())
            .claim("perms", user.getPermissions())
            .claim("sa", user.isSuperAdmin())
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(accessMinutes * 60)))
            .signWith(key)
            .compact();
    }

    /** @return authenticated principal reconstructed from verified claims, or null if invalid. */
    @SuppressWarnings("unchecked")
    public UserPrincipal parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return new UserPrincipal(
                UUID.fromString(c.getSubject()),
                UUID.fromString(c.get("org", String.class)),
                c.get("email", String.class),
                c.get("name", String.class),
                null, true, Boolean.TRUE.equals(c.get("sa", Boolean.class)),
                Set.copyOf((java.util.List<String>) c.get("roles", java.util.List.class)),
                Set.copyOf((java.util.List<String>) c.get("perms", java.util.List.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
