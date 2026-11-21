package com.aquasuite.ims.dto.catalog.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CategoryBriefResponse(
        UUID categoryId,
        String categoryCode,
        String categoryName
) {}
