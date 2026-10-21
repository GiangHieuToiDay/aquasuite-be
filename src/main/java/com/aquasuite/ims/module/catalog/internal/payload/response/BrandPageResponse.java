package com.aquasuite.ims.module.catalog.internal.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandPageResponse {

    private List<BrandResponse> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
