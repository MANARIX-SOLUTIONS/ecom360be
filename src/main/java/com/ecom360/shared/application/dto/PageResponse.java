package com.ecom360.shared.application.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Generic paginated response wrapper.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean hasNext,
        boolean hasPrevious
) {

    @JsonCreator
    public PageResponse(
            @JsonProperty("content") List<T> content,
            @JsonProperty("page") int page,
            @JsonProperty("size") int size,
            @JsonProperty("totalElements") long totalElements
    ) {
        this(
                content, page, size, totalElements,
                size > 0 ? (int) Math.ceil((double) totalElements / size) : 0,
                page == 0,
                size > 0 && page >= Math.ceil((double) totalElements / size) - 1,
                size > 0 && page < Math.ceil((double) totalElements / size) - 1,
                page > 0
        );
    }

    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }
}
