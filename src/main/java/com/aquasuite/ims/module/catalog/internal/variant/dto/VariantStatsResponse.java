package com.aquasuite.ims.module.catalog.internal.variant.dto;

public record VariantStatsResponse(long total, long active, long inactive, long missingRetailPrice) { }
