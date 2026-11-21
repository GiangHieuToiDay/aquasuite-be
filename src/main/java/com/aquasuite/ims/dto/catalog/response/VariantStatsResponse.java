package com.aquasuite.ims.dto.catalog.response;

public record VariantStatsResponse(long total, long active, long inactive, long missingRetailPrice) {
}
