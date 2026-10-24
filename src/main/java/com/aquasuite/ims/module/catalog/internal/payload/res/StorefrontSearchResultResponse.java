package com.aquasuite.ims.module.catalog.internal.payload.res;

import lombok.Builder;
import org.springframework.data.domain.Page;

import java.util.List;

@Builder
public record StorefrontSearchResultResponse(
        Page<StorefrontProductCardResponse> products,
        String message,
        List<CategoryBriefResponse> popularCategories,
        ProductFacetResponse facets
) {}
