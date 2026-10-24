package com.aquasuite.ims.share.util;

import com.aquasuite.ims.share.dto.PageQuery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Utility class for sanitizing and building Spring Data {@link Pageable} instances.
 * <p>
 * Key Security & Reliability Capabilities:
 * 1. DoS Mitigation: Limits page size to {@value #MAX_PAGE_SIZE} items per page.
 * 2. Injection & Crash Prevention: Validates sort fields against an allowed whitelist
 *    to prevent Hibernate PropertyReferenceException and malicious query parameter manipulation.
 */
public final class PageableUtils {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final Sort.Direction DEFAULT_DIRECTION = Sort.Direction.DESC;

    private PageableUtils() {
        // Utility class, prevent instantiation
    }

    /**
     * Sanitizes the page index. Guarantees non-negative integer.
     */
    public static int sanitizePage(int page) {
        return Math.max(DEFAULT_PAGE, page);
    }

    /**
     * Sanitizes the page size.
     * Guarantees 1 <= size <= {@value #MAX_PAGE_SIZE}.
     * If non-positive, falls back to {@value #DEFAULT_PAGE_SIZE}.
     */
    public static int sanitizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * Sanitizes sort direction string ("ASC" or "DESC").
     * Returns the provided defaultDirection if sortDir is invalid or null.
     */
    public static Sort.Direction sanitizeDirection(String sortDir, Sort.Direction defaultDirection) {
        if (sortDir == null || sortDir.isBlank()) {
            return defaultDirection != null ? defaultDirection : DEFAULT_DIRECTION;
        }
        String trimmed = sortDir.trim();
        if ("ASC".equalsIgnoreCase(trimmed)) {
            return Sort.Direction.ASC;
        }
        if ("DESC".equalsIgnoreCase(trimmed)) {
            return Sort.Direction.DESC;
        }
        return defaultDirection != null ? defaultDirection : DEFAULT_DIRECTION;
    }

    /**
     * Sanitizes sort attribute name by checking against a whitelist.
     *
     * @param sortBy            Requested sort field from client
     * @param allowedSortFields Whitelist of allowed entity property names
     * @param defaultSortBy     Fallback sort field if sortBy is missing or not allowed
     * @return Whitelisted field name or defaultSortBy
     */
    public static String sanitizeSortBy(String sortBy, Set<String> allowedSortFields, String defaultSortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return defaultSortBy;
        }
        String trimmed = sortBy.trim();
        if (allowedSortFields != null && allowedSortFields.contains(trimmed)) {
            return trimmed;
        }
        return defaultSortBy;
    }

    /**
     * Creates a {@link Pageable} from primitive parameters with whitelist validation and default DESC direction.
     */
    public static Pageable createPageable(
            int page,
            int size,
            String sortBy,
            String sortDir,
            Set<String> allowedSortFields,
            String defaultSortBy
    ) {
        return createPageable(page, size, sortBy, sortDir, allowedSortFields, defaultSortBy, DEFAULT_DIRECTION);
    }

    /**
     * Creates a {@link Pageable} from primitive parameters with whitelist validation and custom default direction.
     */
    public static Pageable createPageable(
            int page,
            int size,
            String sortBy,
            String sortDir,
            Set<String> allowedSortFields,
            String defaultSortBy,
            Sort.Direction defaultDirection
    ) {
        int safePage = sanitizePage(page);
        int safeSize = sanitizeSize(size);
        String safeSortBy = sanitizeSortBy(sortBy, allowedSortFields, defaultSortBy);
        Sort.Direction safeDirection = sanitizeDirection(sortDir, defaultDirection);

        if (safeSortBy == null || safeSortBy.isBlank()) {
            return PageRequest.of(safePage, safeSize);
        }

        return PageRequest.of(safePage, safeSize, Sort.by(safeDirection, safeSortBy));
    }

    /**
     * Creates a {@link Pageable} from a {@link PageQuery} object with default DESC direction.
     */
    public static Pageable toPageable(
            PageQuery query,
            Set<String> allowedSortFields,
            String defaultSortBy
    ) {
        return toPageable(query, allowedSortFields, defaultSortBy, DEFAULT_DIRECTION);
    }

    /**
     * Creates a {@link Pageable} from a {@link PageQuery} object with custom default direction.
     */
    public static Pageable toPageable(
            PageQuery query,
            Set<String> allowedSortFields,
            String defaultSortBy,
            Sort.Direction defaultDirection
    ) {
        if (query == null) {
            return createPageable(
                    DEFAULT_PAGE,
                    DEFAULT_PAGE_SIZE,
                    null,
                    null,
                    allowedSortFields,
                    defaultSortBy,
                    defaultDirection
            );
        }
        return createPageable(
                query.getPage(),
                query.getSize(),
                query.getSortBy(),
                query.getSortDir(),
                allowedSortFields,
                defaultSortBy,
                defaultDirection
        );
    }

    /**
     * Creates a {@link Pageable} from sanitized page and size with a pre-configured {@link Sort}.
     */
    public static Pageable createPageable(int page, int size, Sort sort) {
        int safePage = sanitizePage(page);
        int safeSize = sanitizeSize(size);
        if (sort == null || sort.isUnsorted()) {
            return PageRequest.of(safePage, safeSize);
        }
        return PageRequest.of(safePage, safeSize, sort);
    }
}
