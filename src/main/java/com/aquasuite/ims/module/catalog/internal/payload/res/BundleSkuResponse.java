package com.aquasuite.ims.module.catalog.internal.payload.res;

import java.math.BigDecimal;
import java.util.*;

/**
 * Một SKU đủ điều kiện trong kết quả tìm thành phần combo, gồm tên sản phẩm và giá lẻ. Luồng:
 * BundleQueryService.searchSkus lọc SKU ACTIVE thuộc product NORMAL ACTIVE → MapStruct. Client lấy
 * variantId để gửi trong BundleItemRequest; không có giá vốn hoặc tồn kho.
 */
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
