package com.aquasuite.ims.module.catalog.internal.payload;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
/**
 * Ảnh chụp giá/tồn tại lúc xem trước, không giữ hàng hoặc bảo đảm cho giao dịch sau.
 * packagePrice là giá một combo; các tổng tiền và requiredQty đã nhân quantity.
 * missingVariantIds chỉ chứa SKU xác định là thiếu; availableQty null nghĩa là chưa biết tồn.
 * purchasable chỉ đúng khi không có lỗi giá/thành phần và toàn bộ SKU đủ tồn tại lúc kiểm.
 */
public record BundleEvaluation(UUID bundleId, Long bundleVersion, Instant evaluatedAt, int quantity,
        BigDecimal packagePrice, BigDecimal totalPackagePrice, BigDecimal originalTotal,
        BigDecimal savingAmount, BigDecimal discountPercent, String priceIssue,
        String availability, boolean purchasable, List<UUID> missingVariantIds, List<Item> items) {
    public record Item(UUID variantId, String sku, String name, int requiredQty, BigDecimal availableQty) {}
}
