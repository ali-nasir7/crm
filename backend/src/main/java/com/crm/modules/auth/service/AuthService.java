package com.crm.modules.auth.service;

import com.crm.common.api.ApiException;
import com.crm.common.util.Normalizer;
import com.crm.config.CrmProperties;
import com.crm.modules.auth.domain.RefreshToken;
import com.crm.modules.auth.dto.AuthDtos.*;
import com.crm.modules.auth.repo.RefreshTokenRepository;
import com.crm.modules.identity.domain.User;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.organization.repo.OrganizationRepository;
import com.crm.modules.identity.service.UserPrincipalFactory;
import com.crm.security.JwtService;
import com.crm.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UserPrincipalFactory principalFactory;
    private final OrganizationRepository organizations;
    private final CrmProperties props;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public TokenResponse login(LoginRequest request, HttpServletRequest http) {
        String email = Normalizer.email(request.email());
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            if (users.count() == 0) {
                // Self-diagnosing: an empty users table means bootstrap seeding never completed.
                throw ApiException.business(
                    "No users exist in this database yet — the dev data seeder did not complete. "
                    + "Check backend logs for 'SEEDING FAILED', then reset with: docker compose down -v && docker compose up --build");
            }
            throw ApiException.unauthorized("Invalid email or password");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw ApiException.business("Account temporarily locked due to failed login attempts. Try again later.");
        }
        if (user.getStatus() != com.crm.modules.identity.domain.UserStatus.ACTIVE) {
            throw ApiException.forbidden("Account is not active");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailure(user);
            throw ApiException.unauthorized("Invalid email or password");
        }

        if (user.isMustChangePassword()) {
            // Correct credentials, but the account still runs on an admin-issued temp password.
            // No tokens are issued until the user completes onboarding.
            throw ApiException.passwordChangeRequired(
                "Your account uses a temporary password. Set your own password to activate the account.");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        users.save(user);

        UserPrincipal principal = principalFactory.from(user);
        return issueTokens(principal, user, http);
    }

    /**
     * First-login activation for admin-created users: verifies the temp password, applies the
     * new password (same policy as everywhere) and clears the must-change flag.
     */
    @Transactional
    public void completeOnboarding(CompleteOnboardingRequest request) {
        String email = Normalizer.email(request.email());
        User user = users.findByEmailIgnoreCase(email)
            .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (!passwordEncoder.matches(request.tempPassword(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        if (!user.isMustChangePassword()) {
            throw ApiException.badRequest("This account does not need onboarding");
        }
        validatePasswordPolicy(request.newPassword());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        users.save(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request, HttpServletRequest http) {
        String hash = sha256(request.refreshToken());
        RefreshToken stored = refreshTokens.findByTokenHash(hash).orElse(null);

        if (stored == null) {
            // Unknown token: possible theft — do nothing beyond rejecting.
            throw ApiException.unauthorized("Invalid refresh token");
        }
        if (!stored.isActive()) {
            // Reuse of a rotated/revoked token → revoke the whole lineage for this user.
            refreshTokens.revokeAllForUser(stored.getUserId(), Instant.now());
            log.warn("Refresh token reuse detected for user {}; all sessions revoked", stored.getUserId());
            throw ApiException.unauthorized("Refresh token expired or reused. Please log in again.");
        }

        User user = users.findById(stored.getUserId()).orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
        if (user.getStatus() != com.crm.modules.identity.domain.UserStatus.ACTIVE) {
            throw ApiException.forbidden("Account is not active");
        }

        stored.setRevokedAt(Instant.now());
        refreshTokens.save(stored);

        UserPrincipal principal = principalFactory.from(user);
        return issueTokens(principal, user, http);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokens.findByTokenHash(sha256(request.refreshToken()))
            .filter(RefreshToken::isActive)
            .ifPresent(t -> { t.setRevokedAt(Instant.now()); refreshTokens.save(t); });
    }

    @Transactional
    public void changePassword(UserPrincipal principal, ChangePasswordRequest request) {
        User user = users.findById(principal.getId()).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        validatePasswordPolicy(request.newPassword());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(user);
        refreshTokens.revokeAllForUser(user.getId(), Instant.now()); // force re-login everywhere
    }

    private TokenResponse issueTokens(UserPrincipal principal, User user, HttpServletRequest http) {
        String access = jwtService.createAccessToken(principal);
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(sha256(refresh));
        rt.setExpiresAt(Instant.now().plus(Duration.ofDays(props.app().jwtRefreshDays())));
        rt.setIp(clientIp(http));
        rt.setUserAgent(truncate(http.getHeader("User-Agent"), 255));
        refreshTokens.save(rt);

        return new TokenResponse(access, refresh, props.app().jwtAccessMinutes() * 60L, toUserInfo(user, principal));
    }

    @Transactional(readOnly = true)
    public MeResponse me(java.util.UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        UserPrincipal principal = principalFactory.from(user);
        String orgName = organizations.findById(user.getOrganizationId()).map(o -> o.getName()).orElse(null);
        return new MeResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
            user.displayName(), user.isSuperAdmin(), principal.getRoles(), principal.getPermissions(),
            user.getDailyTargets(), orgName, user.getOrganizationId());
    }

    public static void validatePasswordPolicy(String password) {
        if (password == null || password.length() < 10 || !password.chars().anyMatch(Character::isLetter)
                || !password.chars().anyMatch(Character::isDigit)) {
            throw ApiException.badRequest("Password must be at least 10 characters and contain letters and digits");
        }
    }

    private void registerFailure(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= 5) {
            user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(15)));
            user.setFailedLoginAttempts(0);
            log.warn("User {} locked out for 15 minutes after repeated failures", user.getEmail());
        }
        users.save(user);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String clientIp(HttpServletRequest http) {
        String fwd = http.getHeader("X-Forwarded-For");
        return fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : http.getRemoteAddr();
    }

    private String truncate(String s, int max) { return s == null ? null : s.substring(0, Math.min(s.length(), max)); }

    public static UserInfo toUserInfo(User user, UserPrincipal principal) {
        return new UserInfo(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.displayName(),
            principal.isSuperAdmin(), principal.getRoles(), principal.getPermissions(), user.getDailyTargets(), null);
    }
}
