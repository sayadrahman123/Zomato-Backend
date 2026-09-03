package com.zomato.backend.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Standardized pagination wrapper for all paginated REST endpoints.
 * <p>
 * Replaces Spring Data's raw {@link Page} (PageImpl) in API responses to:
 * <ul>
 *   <li>Provide a clean, predictable, and portable JSON structure.</li>
 *   <li>Avoid leaking internal Spring framework fields (e.g. unpaged, pageable).</li>
 *   <li>Ensure seamless Jackson JSON serialization and Redis caching compatibility.</li>
 * </ul>
 *
 * @param <T> the type of items in the page
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean isFirst,
        boolean isLast,
        boolean hasNext,
        boolean hasPrevious
) {

    /**
     * Factory method to convert a Spring Data {@link Page} into a {@link PagedResponse}.
     *
     * @param page Spring Data page
     * @param <T>  item type
     * @return standardized PagedResponse
     */
    public static <T> PagedResponse<T> from(Page<T> page) {
        if (page == null) {
            return new PagedResponse<>(List.of(), 0, 0, 0L, 0, true, true, false, false);
        }
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.hasNext(),
                page.hasPrevious()
        );
    }
}
