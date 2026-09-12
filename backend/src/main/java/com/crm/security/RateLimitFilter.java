package com.crm.security;

import com.crm.common.api.ApiException;
import com.crm.common.util.Normalizer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Redis fixed-window rate limiting: strict on auth endpoints (brute-force protection),
 * generous on the API. Fails OPEN with a warning when Redis is unavailable (availability > DoS
 * resistance on the internal network; a production edge proxy should also rate-limit).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redis;

    /**
     * Explicit opt-out for environments without Redis (local dev). Brute-force ACCOUNT lockout
     * (5 failed logins -> 15 min) lives in the database and stays active regardless.
     */
    @Value("${crm.ratelimit.enabled:true}")
    private boolean enabled;

    private static final int LOGIN_LIMIT = 10;
    private static final int API_LIMIT = 600;

    private volatile long lastWarnAt = 0;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(req, res);
            return;
        }
        try {
            String path = req.getRequestURI();
            if (path.startsWith("/api/v1/auth/login") || path.startsWith("/api/v1/auth/refresh")) {
                check("rl:login:" + Normalizer.email(req.getParameter("email") != null ? req.getParameter("email") : clientIp(req)), LOGIN_LIMIT, Duration.ofMinutes(1));
            } else if (path.startsWith("/api/")) {
                String who = CurrentUser.idOrNull() != null ? CurrentUser.idOrNull().toString() : clientIp(req);
                check("rl:api:" + who, API_LIMIT, Duration.ofMinutes(1));
            }
        } catch (ApiException e) {
            res.setStatus(e.getStatus().value());
            res.setContentType("application/json");
            res.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"" + e.getMessage() + "\"}");
            return;
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            if (now - lastWarnAt > 60_000) {   // warn once per minute, not once per request
                lastWarnAt = now;
                log.warn("Rate limiter unavailable, failing open: {}. Start Redis or set CRM_RATE_LIMIT=false to silence.", e.getMessage());
            }
        }
        chain.doFilter(req, res);
    }

    private void check(String key, int limit, Duration window) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) redis.expire(key, window);
        if (count != null && count > limit) {
            throw ApiException.rateLimited("Too many requests. Try again in a minute.");
        }
    }

    private String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        return fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : req.getRemoteAddr();
    }
}
