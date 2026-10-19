package com.aquasuite.ims.module.catalog.internal.payload;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.*;

public record BundleSkuResponse(
        UUID variantId,
        UUID productId,
        String sku,
        String productName,
        String variantName,
        String unit,
        String color,
        String size,
        BigDecimal sellingPrice) {}
