package com.aquasuite.ims.module.sales.api;

import com.aquasuite.ims.module.sales.api.dto.VariantDocumentUsage;

import java.util.UUID;

public interface SalesVariantUsageApi {
    VariantDocumentUsage getVariantDocumentUsage(UUID variantId);
    void removeVariantFromCarts(UUID variantId);
}
