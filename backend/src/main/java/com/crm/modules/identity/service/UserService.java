package com.crm.modules.identity.service;

import com.crm.common.api.ApiException;
import com.crm.common.api.PageResponse;
import com.crm.modules.identity.domain.*;
import com.crm.modules.identity.dto.IdentityDtos.*;
import com.crm.modules.identity.repo.RoleRepository;
import com.crm.modules.identity.repo.TeamRepository;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.auth.service.AuthService;
import com.crm.modules.organization.repo.OrganizationRepository;
import com.crm.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final TeamRepository teams;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationRepository organizations;
    private final com.crm.modules.audit.service.AuditService audit;
    private final OnboardingMailService onboardingMail;

    @Transactional(readOnly = true)
    public PageResponse<UserItem> list(UUID orgId, String query, String roleKey, UUID teamId, int page, int size) {
        Specification<User> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isNull(root.get("deletedAt")));
            if (query != null && !query.isBlank()) {
                String q = "%" + query.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("firstName")), q), cb.like(cb.lower(root.get("lastName")), q),
                    cb.like(cb.lower(root.get("email")), q)));
            }
            if (roleKey != null && !roleKey.isBlank()) {
                ps.add(root.join("roles").get("key").in(List.of(roleKey.toUpperCase())));
            }
            if (teamId != null) {
                ps.add(root.join("teams").get("id").in(List.of(teamId)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<UserItem> result = users.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by("firstName")))
            .map(u -> toItem(u, null));
        return PageResponse.of(result.getContent(), result.getPageable(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public UserItem get(UUID orgId, UUID id) {
        User u = users.findById(id).filter(x -> x.getOrganizationId().equals(orgId))
            .orElseThrow(() -> ApiException.notFound("User not found"));
        return toItem(u, null);
    }

    @Transactional
    public UserItem create(UUID orgId, CreateUserRequest req) {
        if (users.findByEmailIgnoreCase(req.email()).isPresent()) throw ApiException.conflict("Email is already in use");
        User u = new User();
        u.setOrganizationId(orgId);
        u.setEmail(req.email().trim().toLowerCase());

        // Password flow: admin may set one explicitly, otherwise a secure temp password is
        // generated, hashed, and (when SMTP is configured) emailed. The user must change it
        // at first login. Plaintext is never stored and never logged.
        String tempPassword = null;
        if (req.password() != null && !req.password().isBlank()) {
            AuthService.validatePasswordPolicy(req.password());
            u.setPasswordHash(passwordEncoder.encode(req.password()));
            u.setMustChangePassword(false);
        } else {
            tempPassword = generateTempPassword();
            u.setPasswordHash(passwordEncoder.encode(tempPassword));
            u.setMustChangePassword(true);
        }

        applyProfile(u, req.firstName(), req.lastName(), req.jobTitle(), req.phone(), null, req.roleKeys(), req.teamIds(), req.dailyTargets());
        users.save(u);
        audit.log("USER_CREATE", "USER", u.getId(), u.getEmail(), null, Map.of("email", u.getEmail()));

        boolean emailed = false;
        if (tempPassword != null) {
            String orgName = organizations.findById(orgId).map(o -> o.getName()).orElse(null);
            emailed = onboardingMail.sendOnboarding(u.getEmail(), u.displayName(), tempPassword, orgName);
        }
        // Expose the temp password ONLY when the email could not be delivered, so the admin
        // can relay it manually. When emailed, it travels by email only.
        return toItem(u, null, emailed ? null : tempPassword);
    }

    @Transactional
    public UserItem update(UUID orgId, UUID id, UpdateUserRequest req) {
        User u = users.findById(id).filter(x -> x.getOrganizationId().equals(orgId))
            .orElseThrow(() -> ApiException.notFound("User not found"));
        Map<String, Object> before = Map.of("status", u.getStatus(), "roles", u.getRoles().stream().map(Role::getKey).toList());
        UserStatus newStatus = req.status() != null ? UserStatus.valueOf(req.status()) : u.getStatus();
        applyProfile(u, req.firstName(), req.lastName(), req.jobTitle(), req.phone(), newStatus, req.roleKeys(), req.teamIds(), req.dailyTargets());
        users.save(u);
        audit.log("USER_UPDATE", "USER", u.getId(), u.getEmail(), before, Map.of("status", String.valueOf(newStatus)));
        return toItem(u, null);
    }

    @Transactional
    public void delete(UUID orgId, UUID id) {
        User u = users.findById(id).filter(x -> x.getOrganizationId().equals(orgId))
            .orElseThrow(() -> ApiException.notFound("User not found"));
        if (u.getId().equals(CurrentUser.require().getId())) throw ApiException.business("You cannot delete your own account");
        u.setStatus(UserStatus.DISABLED);
        u.setDeletedAt(Instant.now());
        users.save(u);
        audit.log("USER_DELETE", "USER", u.getId(), u.getEmail(), null, null);
    }

    private void applyProfile(User u, String firstName, String lastName, String jobTitle, String phone,
                              UserStatus status, List<String> roleKeys, List<UUID> teamIds, Map<String, Integer> targets) {
        u.setFirstName(firstName.trim());
        u.setLastName(lastName.trim());
        u.setJobTitle(jobTitle);
        u.setPhone(phone);
        if (status != null) u.setStatus(status);
        if (targets != null) u.setDailyTargets(targets);
        if (roleKeys != null) {
            Set<Role> newRoles = new HashSet<>();
            for (String key : roleKeys) {
                Role r = roles.findByOrganizationIdAndKey(u.getOrganizationId(), key.toUpperCase())
                    .orElseThrow(() -> ApiException.badRequest("Unknown role: " + key));
                newRoles.add(r);
            }
            u.setRoles(newRoles);
        }
        if (teamIds != null) {
            Set<Team> newTeams = new HashSet<>();
            for (UUID teamId : teamIds) {
                Team t = teams.findById(teamId).filter(t2 -> t2.getOrganizationId().equals(u.getOrganizationId()))
                    .orElseThrow(() -> ApiException.badRequest("Unknown team"));
                newTeams.add(t);
            }
            u.getTeams().clear();
            u.getTeams().addAll(newTeams);
            for (Team t : newTeams) t.getMembers().add(u);
        }
    }

    public UserItem toItem(User u, String organizationName) {
        return toItem(u, organizationName, null);
    }

    public UserItem toItem(User u, String organizationName, String tempPassword) {
        return new UserItem(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.displayName(),
            u.getJobTitle(), u.getPhone(), u.getStatus().name(), u.isSuperAdmin(),
            u.getRoles().stream().map(r -> r.getKey()).collect(java.util.stream.Collectors.toSet()),
            u.getTeams().stream().map(t -> new TeamSummary(t.getId(), t.getName())).toList(),
            u.getDailyTargets(), u.getLastLoginAt(), u.getCreatedAt(), tempPassword);
    }

    /**
     * Admin-initiated password reset: generates a compliant temp password, clears lockout,
     * audit-logs the action (NEVER the password value). If sendEmail is requested, returns an
     * honest 422 because no email provider is configured in this environment.
     */
    @Transactional
    public Map<String, String> resetPassword(UUID orgId, UUID targetUserId, boolean sendEmail) {
        if (sendEmail) {
            throw com.crm.common.api.ApiException.business(
                "Email delivery is not configured in this environment (Integration Required). "
                + "Copy the temp password and share it over a secure channel instead.");
        }
        User u = users.findById(targetUserId)
            .filter(x -> x.getOrganizationId().equals(orgId))
            .orElseThrow(() -> ApiException.notFound("User not found"));
        String temp = generateTempPassword();
        u.setPasswordHash(passwordEncoder.encode(temp));
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        users.save(u);
        audit.log("USER_RESET_PASSWORD", "USER", u.getId(), u.getEmail(), null,
            Map.of("resetBy", String.valueOf(CurrentUser.idOrNull())));
        return Map.of("tempPassword", temp);
    }

    /** >= 10 chars, letters AND digits (matches the auth policy), cryptographically random. */
    private String generateTempPassword() {
        var rnd = new java.security.SecureRandom();
        String letters = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz";
        String digits = "23456789";
        List<Character> chars = new ArrayList<>();
        for (int i = 0; i < 8; i++) chars.add(letters.charAt(rnd.nextInt(letters.length())));
        for (int i = 0; i < 4; i++) chars.add(digits.charAt(rnd.nextInt(digits.length())));
        java.util.Collections.shuffle(chars, rnd);
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }
}
