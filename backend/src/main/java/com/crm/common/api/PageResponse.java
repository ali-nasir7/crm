package com.crm.common.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> p, Function<E, T> mapper) {
        return new PageResponse<>(p.getContent().stream().map(mapper).toList(),
            p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    public static <T> PageResponse<T> of(List<T> content, Pageable pageable, long total) {
        int totalPages = pageable.getPageSize() == 0 ? 1 : (int) Math.ceilDiv(total, pageable.getPageSize());
        return new PageResponse<>(content, pageable.getPageNumber(), pageable.getPageSize(), total, totalPages);
    }
}
