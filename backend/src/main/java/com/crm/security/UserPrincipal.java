package com.crm.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;

/** Authenticated principal. Authorities = permission keys + ROLE_ prefixed role keys. */
@Getter
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final UUID organizationId;
    private final String username;
    private final String displayName;
    private final String password;
    private final boolean active;
    private final boolean superAdmin;
    private final Set<String> roles;
    private final Set<String> permissions;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(UUID id, UUID organizationId, String email, String displayName, String passwordHash,
                         boolean active, boolean superAdmin, Set<String> roles, Set<String> permissions) {
        this.id = id;
        this.organizationId = organizationId;
        this.username = email;
        this.displayName = displayName;
        this.password = passwordHash;
        this.active = active;
        this.superAdmin = superAdmin;
        this.roles = roles;
        this.permissions = permissions;
        Set<GrantedAuthority> auth = new HashSet<>();
        for (String p : permissions) auth.add(new SimpleGrantedAuthority(p));
        for (String r : roles) auth.add(new SimpleGrantedAuthority("ROLE_" + r));
        if (superAdmin) auth.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        this.authorities = Collections.unmodifiableSet(auth);
    }

    public boolean hasPermission(String permission) {
        return superAdmin || permissions.contains(permission);
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return active; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return active; }
}
