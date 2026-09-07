package com.multicloud.quote.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Spring Page 직렬화 형태에 의존하지 않기 위한 페이지 응답 래퍼. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
