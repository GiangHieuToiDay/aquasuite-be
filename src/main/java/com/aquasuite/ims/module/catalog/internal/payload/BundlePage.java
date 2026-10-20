package com.aquasuite.ims.module.catalog.internal.payload;

import jakarta.validation.constraints.*;

import java.util.*;

/**
 * Envelope phân trang dùng chung cho danh sách combo và lookup SKU, nằm trong ApiResponse.data.
 * items là trang hiện tại; page bắt đầu từ 0; totalElements/totalPages tính sau khi lọc.
 * Trang vượt cuối trả items rỗng, không coi là lỗi tài nguyên không tồn tại.
 */
public record BundlePage<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
