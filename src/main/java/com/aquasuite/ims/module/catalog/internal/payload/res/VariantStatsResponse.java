package com.aquasuite.ims.module.catalog.internal.payload.res;

public record VariantStatsResponse(long total, long active, long inactive, long missingRetailPrice) {
}
