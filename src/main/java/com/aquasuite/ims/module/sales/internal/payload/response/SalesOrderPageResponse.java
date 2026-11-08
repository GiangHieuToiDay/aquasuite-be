package com.aquasuite.ims.module.sales.internal.payload.response;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import java.util.List;

/** Explicit JSON contract preserving the existing orders-list pagination shape. */
public record SalesOrderPageResponse(
        List<SalesOrderSummaryResponse> content,
        PageableResponse pageable,
        long totalElements,
        int totalPages,
        int size,
        int number,
        SortResponse sort,
        boolean first,
        boolean last,
        int numberOfElements,
        boolean empty) {

    public static SalesOrderPageResponse from(Page<SalesOrderSummaryResponse> page) {
        var pageable = page.getPageable();
        var paging = new PageableResponse(pageable.getPageNumber(), pageable.getPageSize(),
                SortResponse.from(pageable.getSort()), pageable.getOffset(), pageable.isPaged(), pageable.isUnpaged());
        return new SalesOrderPageResponse(List.copyOf(page.getContent()), paging,
                page.getTotalElements(), page.getTotalPages(), page.getSize(), page.getNumber(),
                SortResponse.from(page.getSort()), page.isFirst(), page.isLast(),
                page.getNumberOfElements(), page.isEmpty());
    }

    public record PageableResponse(int pageNumber, int pageSize, SortResponse sort,
                                   long offset, boolean paged, boolean unpaged) {}

    public record SortResponse(boolean empty, boolean sorted, boolean unsorted) {
        static SortResponse from(Sort sort) {
            return new SortResponse(sort.isEmpty(), sort.isSorted(), sort.isUnsorted());
        }
    }
}
