package com.aquasuite.ims.share.util;

import com.aquasuite.ims.share.dto.PageQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PageableUtilsTest {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "productName", "price");
    private static final String DEFAULT_SORT_BY = "createdAt";

    @Test
    @DisplayName("sanitizePage: Converts negative page to 0")
    void sanitizePage_normalizesNegativeToZero() {
        assertThat(PageableUtils.sanitizePage(-1)).isZero();
        assertThat(PageableUtils.sanitizePage(-999)).isZero();
        assertThat(PageableUtils.sanitizePage(0)).isZero();
        assertThat(PageableUtils.sanitizePage(5)).isEqualTo(5);
    }

    @Test
    @DisplayName("sanitizeSize: Caps at MAX_PAGE_SIZE = 100 to prevent DoS")
    void sanitizeSize_capsAtMaxPageSize() {
        assertThat(PageableUtils.sanitizeSize(100)).isEqualTo(100);
        assertThat(PageableUtils.sanitizeSize(101)).isEqualTo(100);
        assertThat(PageableUtils.sanitizeSize(100000)).isEqualTo(100);
    }

    @Test
    @DisplayName("sanitizeSize: Falls back to DEFAULT_PAGE_SIZE = 20 for non-positive size")
    void sanitizeSize_handlesNonPositiveSize() {
        assertThat(PageableUtils.sanitizeSize(0)).isEqualTo(PageableUtils.DEFAULT_PAGE_SIZE);
        assertThat(PageableUtils.sanitizeSize(-10)).isEqualTo(PageableUtils.DEFAULT_PAGE_SIZE);
        assertThat(PageableUtils.sanitizeSize(15)).isEqualTo(15);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASC", "asc", " Asc "})
    @DisplayName("sanitizeDirection: Parses ASC correctly case-insensitively with whitespace")
    void sanitizeDirection_parsesAsc(String dir) {
        assertThat(PageableUtils.sanitizeDirection(dir, Sort.Direction.DESC))
                .isEqualTo(Sort.Direction.ASC);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DESC", "desc", " Desc "})
    @DisplayName("sanitizeDirection: Parses DESC correctly case-insensitively with whitespace")
    void sanitizeDirection_parsesDesc(String dir) {
        assertThat(PageableUtils.sanitizeDirection(dir, Sort.Direction.ASC))
                .isEqualTo(Sort.Direction.DESC);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "invalid", "DROP TABLE", "123"})
    @DisplayName("sanitizeDirection: Falls back to default direction when string is invalid or malicious")
    void sanitizeDirection_fallsBackOnInvalid(String invalidDir) {
        assertThat(PageableUtils.sanitizeDirection(invalidDir, Sort.Direction.ASC))
                .isEqualTo(Sort.Direction.ASC);
        assertThat(PageableUtils.sanitizeDirection(null, Sort.Direction.DESC))
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("sanitizeSortBy: Accepts whitelisted field")
    void sanitizeSortBy_acceptsWhitelisted() {
        assertThat(PageableUtils.sanitizeSortBy("productName", ALLOWED_SORT_FIELDS, DEFAULT_SORT_BY))
                .isEqualTo("productName");
        assertThat(PageableUtils.sanitizeSortBy(" price ", ALLOWED_SORT_FIELDS, DEFAULT_SORT_BY))
                .isEqualTo("price");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "passwordHash", "secret_key", "id; DROP TABLE", "nonExistentField"})
    @DisplayName("sanitizeSortBy: Rejects non-whitelisted or malicious fields and falls back to default")
    void sanitizeSortBy_rejectsNonWhitelisted(String maliciousSort) {
        assertThat(PageableUtils.sanitizeSortBy(maliciousSort, ALLOWED_SORT_FIELDS, DEFAULT_SORT_BY))
                .isEqualTo(DEFAULT_SORT_BY);
    }

    @Test
    @DisplayName("sanitizeSortBy: Handles null inputs safely")
    void sanitizeSortBy_handlesNullSafely() {
        assertThat(PageableUtils.sanitizeSortBy(null, ALLOWED_SORT_FIELDS, DEFAULT_SORT_BY))
                .isEqualTo(DEFAULT_SORT_BY);
        assertThat(PageableUtils.sanitizeSortBy("productName", null, DEFAULT_SORT_BY))
                .isEqualTo(DEFAULT_SORT_BY);
    }

    @Test
    @DisplayName("toPageable(PageQuery): Builds safe Pageable from PageQuery")
    void toPageable_buildsSafePageable() {
        PageQuery query = PageQuery.builder()
                .page(2)
                .size(50)
                .sortBy("price")
                .sortDir("ASC")
                .build();

        Pageable pageable = PageableUtils.toPageable(query, ALLOWED_SORT_FIELDS, DEFAULT_SORT_BY);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(50);
        assertThat(pageable.getSort().getOrderFor("price")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("price").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("toPageable(null): Handles null query with safe defaults")
    void toPageable_handlesNullQuery() {
        Pageable pageable = PageableUtils.toPageable(null, ALLOWED_SORT_FIELDS, DEFAULT_SORT_BY);

        assertThat(pageable.getPageNumber()).isEqualTo(PageableUtils.DEFAULT_PAGE);
        assertThat(pageable.getPageSize()).isEqualTo(PageableUtils.DEFAULT_PAGE_SIZE);
        assertThat(pageable.getSort().getOrderFor(DEFAULT_SORT_BY)).isNotNull();
        assertThat(pageable.getSort().getOrderFor(DEFAULT_SORT_BY).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("createPageable: DoS and Injection parameters are completely sanitized")
    void createPageable_sanitizesDosAndInjection() {
        Pageable pageable = PageableUtils.createPageable(
                -5,
                999999,
                "inject' OR 1=1;--",
                "maliciousDir",
                ALLOWED_SORT_FIELDS,
                DEFAULT_SORT_BY
        );

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(PageableUtils.MAX_PAGE_SIZE);
        assertThat(pageable.getSort().getOrderFor(DEFAULT_SORT_BY)).isNotNull();
        assertThat(pageable.getSort().getOrderFor(DEFAULT_SORT_BY).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("createPageable: Unsorted when no default or valid sort is available")
    void createPageable_unsortedWhenNoSortProvided() {
        Pageable pageable = PageableUtils.createPageable(
                0,
                10,
                null,
                null,
                ALLOWED_SORT_FIELDS,
                null
        );

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().isUnsorted()).isTrue();
    }
}
