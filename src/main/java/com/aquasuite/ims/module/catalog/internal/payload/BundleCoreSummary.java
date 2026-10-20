package com.aquasuite.ims.module.catalog.internal.payload;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Một dòng combo trong response danh sách quản trị, không chứa toàn bộ items/mô tả.
 * Luồng: service đọc theo lô → assembler tạo detail → MapStruct rút ra summary.
 * Giá là giá đọc hiện tại; priceIssue cho biết dữ liệu cần sửa, không cam kết giá bán.
 */
public record BundleCoreSummary(
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
        Instant updatedAt) {}
