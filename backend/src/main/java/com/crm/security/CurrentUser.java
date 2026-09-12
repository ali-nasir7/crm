package com.crm.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class CurrentUser {
    private CurrentUser() {}

    public static UserPrincipal principalOrNull() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof UserPrincipal p ? p : null;
    }

    public static UserPrincipal require() {
        UserPrincipal p = principalOrNull();
        if (p == null) throw new IllegalStateException("No authenticated principal in context");
        return p;
    }

    public static UUID idOrNull() {
        UserPrincipal p = principalOrNull();
        return p == null ? null : p.getId();
    }

    public static UUID orgIdOrNull() {
        UserPrincipal p = principalOrNull();
        return p == null ? null : p.getOrganizationId();
    }
}
