package com.aquasuite.ims.module.catalog.internal.payload.excel;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * DTO đại diện cho 17 cột của 1 dòng dữ liệu trong sheet Import_Data.
 */
@Builder
public record ProductExcelRowDto(
        int rowNumber,                      // Số dòng trên Excel (từ dòng 3 trở đi)
        String productCode,                 // Cột 0 (A): Mã nhóm SP (product_code) *
        String productName,                 // Cột 1 (B): Tên sản phẩm gốc (product_name) *
        String categoryCode,                // Cột 2 (C): Mã danh mục (category_code) *
        String brandCode,                   // Cột 3 (D): Mã thương hiệu (brand_code)
        String sku,                         // Cột 4 (E): Mã SKU (sku) *
        String variantName,                 // Cột 5 (F): Tên biến thể (variant_name) *
        String unit,                        // Cột 6 (G): Đơn vị tính (unit) *
        String color,                       // Cột 7 (H): Màu sắc (color)
        String size,                        // Cột 8 (I): Kích thước (size)
        BigDecimal costPrice,               // Cột 9 (J): Giá vốn (cost_price) *
        BigDecimal sellingPrice,            // Cột 10 (K): Giá bán (selling_price) *
        boolean serialTracking,             // Cột 11 (L): Quản lý Serial (YES/NO -> boolean)
        Map<String, Object> technicalSpec,  // Cột 12 (M): Thông số kỹ thuật -> JSONB
        List<String> imageUrls,             // Cột 13 (N): Link hình ảnh (phân tách dấu phẩy)
        String description,                 // Cột 14 (O): Mô tả (description)
        BigDecimal initialStock,            // Cột 15 (P): Tồn kho đầu kỳ (initial_stock)
        BigDecimal minimumQty               // Cột 16 (Q): Định mức tồn tối thiểu (minimum_qty)
) {}
