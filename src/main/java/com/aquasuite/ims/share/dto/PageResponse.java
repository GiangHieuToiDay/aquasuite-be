package com.aquasuite.ims.share.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Standardized Pagination Response DTO for REST APIs in AquaSuite.
 * Wraps Spring Data Page into a consistent JSON response.
 *
 * @param <T> Response item type
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResponse<T> {

    @Builder.Default
    private List<T> items = Collections.emptyList();
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    /**
     * Creates a PageResponse from a Spring Data Page.
     *
     * @param pageData Spring Data Page
     * @param <T>      Item type
     * @return Standardized PageResponse
     */
    public static <T> PageResponse<T> from(Page<T> pageData) {
        Objects.requireNonNull(pageData, "pageData must not be null");

        return PageResponse.<T>builder()
                .items(List.copyOf(pageData.getContent()))
                .page(pageData.getNumber())
                .size(pageData.getSize())
                .totalElements(pageData.getTotalElements())
                .totalPages(pageData.getTotalPages())
                .build();
    }

    /**
     * Creates a PageResponse from a Spring Data Page while transforming elements using a converter function.
     *
     * @param pageData  Source Spring Data Page
     * @param converter Mapping function from S to T
     * @param <S>       Source item type
     * @param <T>       Target item type
     * @return Standardized PageResponse with converted items
     */
    public static <S, T> PageResponse<T> from(Page<S> pageData, Function<S, T> converter) {
        Objects.requireNonNull(converter, "converter must not be null");

        Objects.requireNonNull(pageData, "pageData must not be null");

        List<T> convertedItems = pageData.getContent().stream()
                .map(converter)
                .toList();

        return PageResponse.<T>builder()
                .items(convertedItems)
                .page(pageData.getNumber())
                .size(pageData.getSize())
                .totalElements(pageData.getTotalElements())
                .totalPages(pageData.getTotalPages())
                .build();
    }

    /**
     * Manual builder constructor with safety defaults.
     */
    public static <T> PageResponse<T> of(
            List<T> items, int page, int size, long totalElements, int totalPages) {
        return PageResponse.<T>builder()
                .items(items != null ? List.copyOf(items) : Collections.emptyList())
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }
}
