package com.crm.modules.counters.service;

import com.crm.modules.counters.repo.CounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CounterService {

    private final CounterRepository counters;

    @Transactional
    public long next(UUID orgId, String key) {
        return counters.nextValue(UUID.randomUUID(), orgId, key);
    }
}
