package com.aquasuite.ims.dto.catalog.response;

import lombok.Builder;

import java.util.UUID;

/** Public brand projection for consumers outside Catalog. */
@Builder
public record CatalogBrandSummaryDto(UUID brandId, String brandCode, String brandName, String status) {
}
