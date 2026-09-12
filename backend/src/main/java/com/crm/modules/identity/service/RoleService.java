package com.crm.modules.identity.service;

import com.crm.common.api.ApiException;
import com.crm.modules.identity.domain.DataScope;
import com.crm.modules.identity.domain.Permission;
import com.crm.modules.identity.domain.Role;
import com.crm.modules.identity.dto.IdentityDtos.*;
import com.crm.modules.identity.repo.PermissionRepository;
import com.crm.modules.identity.repo.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;

    @Transactional(readOnly = true)
    public List<RoleItem> list(UUID orgId) {
        return roles.findByOrganizationIdOrderByNameAsc(orgId).stream()
            .map(r -> new RoleItem(r.getId(), r.getKey(), r.getName(), r.getDescription(), r.getDataScope().name(),
                r.isSystem(), r.getPermissions().stream().map(Permission::getKey).collect(java.util.stream.Collectors.toSet()),
                0L))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionItem> allPermissions() {
        return permissions.findAllByOrderByCategoryAscKeyAsc().stream()
            .map(p -> new PermissionItem(p.getKey(), p.getName(), p.getCategory())).toList();
    }

    @Transactional
    public RoleItem create(UUID orgId, RoleRequest req) {
        String key = req.name().trim().toUpperCase().replaceAll("[^A-Z0-9_]+", "_");
        if (roles.existsByOrganizationIdAndKey(orgId, key)) throw ApiException.conflict("Role key already exists");
        Role r = new Role();
        r.setOrganizationId(orgId);
        r.setKey(key);
        apply(r, req);
        roles.save(r);
        return toItem(r);
    }

    @Transactional
    public RoleItem update(UUID orgId, UUID id, RoleRequest req) {
        Role r = roles.findById(id).filter(x -> x.getOrganizationId().equals(orgId))
            .orElseThrow(() -> ApiException.notFound("Role not found"));
        if (r.isSystem() && req.permissionKeys() == null) throw ApiException.business("System roles require an explicit permission list");
        apply(r, req);
        roles.save(r);
        return toItem(r);
    }

    @Transactional
    public void delete(UUID orgId, UUID id) {
        Role r = roles.findById(id).filter(x -> x.getOrganizationId().equals(orgId))
            .orElseThrow(() -> ApiException.notFound("Role not found"));
        if (r.isSystem()) throw ApiException.business("System roles cannot be deleted");
        roles.delete(r);
    }

    private void apply(Role r, RoleRequest req) {
        r.setName(req.name().trim());
        r.setDescription(req.description());
        if (req.dataScope() != null) {
            try { r.setDataScope(DataScope.valueOf(req.dataScope())); }
            catch (IllegalArgumentException e) { throw ApiException.badRequest("Invalid data scope: " + req.dataScope()); }
        }
        if (req.permissionKeys() != null) {
            Set<Permission> perms = new HashSet<>(permissions.findByKeyIn(req.permissionKeys()));
            if (perms.size() != new HashSet<>(req.permissionKeys()).size()) throw ApiException.badRequest("Unknown permission key in request");
            r.setPermissions(perms);
        }
    }

    private RoleItem toItem(Role r) {
        return new RoleItem(r.getId(), r.getKey(), r.getName(), r.getDescription(), r.getDataScope().name(),
            r.isSystem(), r.getPermissions().stream().map(Permission::getKey).collect(java.util.stream.Collectors.toSet()), 0);
    }
}
