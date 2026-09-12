package com.crm.modules.identity.service;

import com.crm.common.api.ApiException;
import com.crm.modules.identity.domain.Team;
import com.crm.modules.identity.domain.User;
import com.crm.modules.identity.dto.IdentityDtos.*;
import com.crm.modules.identity.repo.TeamRepository;
import com.crm.modules.identity.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teams;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<TeamItem> list(UUID orgId) {
        return teams.findByOrganizationIdOrderByNameAsc(orgId).stream().map(this::toItem).toList();
    }

    @Transactional
    public TeamItem create(UUID orgId, TeamRequest req) {
        Team t = new Team();
        t.setOrganizationId(orgId);
        t.setName(req.name().trim());
        t.setDescription(req.description());
        t.setManagerId(req.managerId());
        teams.save(t);
        return toItem(t);
    }

    @Transactional
    public TeamItem update(UUID orgId, UUID id, TeamRequest req) {
        Team t = find(orgId, id);
        t.setName(req.name().trim());
        t.setDescription(req.description());
        t.setManagerId(req.managerId());
        teams.save(t);
        return toItem(t);
    }

    @Transactional
    public void delete(UUID orgId, UUID id) {
        teams.delete(find(orgId, id));
    }

    @Transactional
    public TeamItem addMembers(UUID orgId, UUID id, MemberRequest req) {
        Team t = find(orgId, id);
        for (UUID userId : req.userIds()) {
            User u = users.findById(userId).filter(x -> x.getOrganizationId().equals(orgId))
                .orElseThrow(() -> ApiException.badRequest("Unknown user " + userId));
            t.getMembers().add(u);
        }
        return toItem(t);
    }

    @Transactional
    public TeamItem removeMember(UUID orgId, UUID id, UUID userId) {
        Team t = find(orgId, id);
        t.getMembers().removeIf(u -> u.getId().equals(userId));
        return toItem(t);
    }

    private Team find(UUID orgId, UUID id) {
        return teams.findById(id).filter(t -> t.getOrganizationId().equals(orgId))
            .orElseThrow(() -> ApiException.notFound("Team not found"));
    }

    private TeamItem toItem(Team t) {
        String managerName = null;
        if (t.getManagerId() != null) {
            managerName = users.findById(t.getManagerId()).map(User::displayName).orElse(null);
        }
        return new TeamItem(t.getId(), t.getName(), t.getDescription(), t.getManagerId(), managerName,
            t.getMembers().stream().map(u -> new UserSummary(u.getId(), u.displayName(), u.getEmail())).toList(),
            t.getCreatedAt());
    }
}
