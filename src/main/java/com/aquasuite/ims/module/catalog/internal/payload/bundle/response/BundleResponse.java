package com.aquasuite.ims.module.catalog.internal.payload.bundle.response;

import com.aquasuite.ims.module.catalog.internal.payload.bundle.request.BundleComponentGroupRequest;
import com.aquasuite.ims.module.catalog.internal.payload.bundle.request.BundleItemRequest;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Response dùng chung cho danh sách, GET, tạo, sửa và đổi trạng thái combo.
 * Luồng: service đọc dữ liệu → assembler tính giá/sellable → MapStruct ánh xạ sang DTO.
 * Timestamp/version thuộc cấu hình combo; không chứa tồn kho, quyền hoặc entity JPA.
 */
public record BundleResponse(
        UUID bundleId,
        String productCode,
        String name,
        String productStatus,
        String publicationStatus,
        Long version,
        int linkedItemsCount,
        BigDecimal originalTotal,
        BigDecimal packagePrice,
        BigDecimal savingAmount,
        BigDecimal discountPercent,
        String priceIssue,
        Instant createdAt,
        Instant updatedAt,
        UUID categoryId,
        UUID brandId,
        String description,
        List<Item> items,
        Instant validFrom,
        Instant validUntil,
        List<BundleComponentGroupRequest> componentGroups) {
    /**
     * Dòng thành phần trong response: giữ ID/SKU để sửa kể cả khi không còn sellable.
     * Khác BundleItemRequest vì có thông tin hiển thị do server ghép, không nhận ngược từ client.
     */
    public record Item(
            UUID bundleItemId,
            UUID variantId,
            String sku,
            String name,
            int quantity,
            BigDecimal sellingPrice,
            boolean sellable) {}
}
