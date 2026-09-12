package com.crm.modules.identity.service;

import com.crm.modules.identity.domain.User;
import com.crm.security.UserPrincipal;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class UserPrincipalFactory {

    public UserPrincipal from(User user) {
        return new UserPrincipal(
            user.getId(),
            user.getOrganizationId(),
            user.getEmail(),
            user.displayName(),
            user.getPasswordHash(),
            user.getStatus() == com.crm.modules.identity.domain.UserStatus.ACTIVE,
            user.isSuperAdmin(),
            user.getRoles().stream().map(r -> r.getKey().toUpperCase()).collect(Collectors.toSet()),
            user.isSuperAdmin()
                ? com.crm.modules.identity.service.PermissionKeys.keys().stream().collect(java.util.stream.Collectors.toSet())
                : user.getRoles().stream().flatMap(r -> r.getPermissions().stream()).map(p -> p.getKey()).collect(Collectors.toSet()));
    }
}
