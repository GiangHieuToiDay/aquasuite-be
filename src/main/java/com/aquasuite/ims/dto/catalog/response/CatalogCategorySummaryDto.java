package com.aquasuite.ims.dto.catalog.response;

import lombok.Builder;

import java.util.UUID;

/** Public category projection for consumers outside Catalog. */
@Builder
public record CatalogCategorySummaryDto(UUID categoryId, String categoryCode, String categoryName, String status) {
}
