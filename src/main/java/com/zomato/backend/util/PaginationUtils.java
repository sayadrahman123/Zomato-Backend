package com.zomato.backend.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * Standard utility for safe, consistent pagination and sorting across the platform.
 * <p>
 * Enforces:
 * <ul>
 *   <li>Page number clamping (non-negative).</li>
 *   <li>Page size clamping between 1 and a configurable ceiling (default max 50) to prevent memory exhaustion / DoS.</li>
 *   <li>Safe sort field validation against whitelists to prevent invalid property exceptions or injection.</li>
 *   <li>Case-insensitive sort direction parsing with safe default fallback.</li>
 * </ul>
 */
public final class PaginationUtils {

    public static final String DEFAULT_PAGE_NUMBER_STR = "0";
    public static final String DEFAULT_PAGE_SIZE_STR   = "10";

    public static final int DEFAULT_PAGE_NUMBER        = 0;
    public static final int DEFAULT_PAGE_SIZE          = 10;
    public static final int DEFAULT_MAX_PAGE_SIZE      = 50;
    public static final String DEFAULT_SORT_DIRECTION  = "DESC";

    private PaginationUtils() {}

    /**
     * Clamps a requested page number so it cannot be negative.
     */
    public static int clampPage(int page) {
        return Math.max(0, page);
    }

    /**
     * Clamps a requested page size between 1 and the specified maximum.
     */
    public static int clampSize(int size, int maxPageSize) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        int effectiveMax = Math.max(1, Math.min(maxPageSize, DEFAULT_MAX_PAGE_SIZE));
        return Math.min(size, effectiveMax);
    }

    /**
     * Creates a validated {@link Pageable} using a fixed {@link Sort}.
     *
     * @param page        requested page number
     * @param size        requested page size
     * @param maxPageSize maximum allowed page size
     * @param sort        sorting specification
     * @return a safe Pageable
     */
    public static Pageable createPageable(int page, int size, int maxPageSize, Sort sort) {
        int validPage = clampPage(page);
        int validSize = clampSize(size, maxPageSize);
        return PageRequest.of(validPage, validSize, sort != null ? sort : Sort.unsorted());
    }

    /**
     * Creates a validated {@link Pageable} with dynamic, whitelist-checked sorting.
     *
     * @param page          requested page number
     * @param size          requested page size
     * @param maxPageSize   maximum allowed page size
     * @param sortBy        requested sort property
     * @param defaultSortBy default sort property if requested is blank or not in whitelist
     * @param sortDir       requested sort direction ("ASC" or "DESC")
     * @param allowedFields whitelist of allowed sort property names
     * @return a safe Pageable
     */
    public static Pageable createPageable(
            int page,
            int size,
            int maxPageSize,
            String sortBy,
            String defaultSortBy,
            String sortDir,
            Set<String> allowedFields
    ) {
        int validPage = clampPage(page);
        int validSize = clampSize(size, maxPageSize);

        String property = defaultSortBy;
        if (StringUtils.hasText(sortBy) && allowedFields != null && allowedFields.contains(sortBy.trim())) {
            property = sortBy.trim();
        }

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = StringUtils.hasText(property) ? Sort.by(direction, property) : Sort.unsorted();

        return PageRequest.of(validPage, validSize, sort);
    }
}
