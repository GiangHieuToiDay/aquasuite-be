package com.aquasuite.ims.dto.catalog.response;

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
