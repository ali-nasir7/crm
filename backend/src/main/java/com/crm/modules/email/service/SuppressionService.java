package com.crm.modules.email.service;

import com.crm.common.api.PageResponse;
import com.crm.modules.email.domain.Suppression;
import com.crm.modules.email.repo.SuppressionRepository;
import com.crm.common.util.Normalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SuppressionService {

    private final SuppressionRepository suppressions;

    @Transactional
    public Suppression add(UUID orgId, String email, Suppression.Reason reason, String note) {
        String normalized = Normalizer.email(email);
        Suppression s = suppressions.findInOrg(orgId, normalized).orElseGet(Suppression::new);
        s.setOrganizationId(orgId);
        s.setEmail(normalized);
        s.setReason(reason == null ? Suppression.Reason.MANUAL : reason);
        s.setNote(note);
        return suppressions.save(s);
    }

    @Transactional(readOnly = true)
    public PageResponse<Suppression> list(UUID orgId, int page, int size) {
        List<Suppression> all = suppressions.findAllInOrg(orgId);
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return PageResponse.of(all.subList(from, to), PageRequest.of(page, size), all.size());
    }

    @Transactional
    public void remove(UUID orgId, UUID id) {
        suppressions.findById(id).filter(s -> s.getOrganizationId().equals(orgId)).ifPresent(suppressions::delete);
    }
}
