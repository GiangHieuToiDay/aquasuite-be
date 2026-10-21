package com.aquasuite.ims.module.catalog.internal.mapper;

import com.aquasuite.ims.module.catalog.internal.payload.excel.ProductExcelRowDto;
import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.enums.ProductType;
import com.aquasuite.ims.module.catalog.internal.enums.VariantStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class ProductExcelMapper {

    /**
     * Tạo mới Bảng 8: products (Sản phẩm gốc - Master Product / SPU)
     */
    public Product toNewProduct(ProductExcelRowDto row, UUID categoryId, UUID brandId) {
        LocalDateTime now = LocalDateTime.now();
        return Product.builder()
                .productCode(row.productCode())
                .categoryId(categoryId)
                .brandId(brandId)
                .productName(row.productName())
                .description(row.description())
                .specifications(Collections.emptyMap())
                .productType(ProductType.NORMAL)
                .status(ProductStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Cập nhật Bảng 8: products khi chọn chế độ OVERWRITE_EXISTING
     */
    public void updateProduct(
            Product product, ProductExcelRowDto row, UUID categoryId, UUID brandId) {
        product.setProductName(row.productName());
        product.setCategoryId(categoryId);
        product.setBrandId(brandId);
        if (row.description() != null && !row.description().isBlank()) {
            product.setDescription(row.description());
        }
        product.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Tạo mới Bảng 11: product_variants (Biến thể sản phẩm - SKU)
     */
    public ProductVariant toNewVariant(ProductExcelRowDto row, UUID productId) {
        LocalDateTime now = LocalDateTime.now();
        return ProductVariant.builder()
                .productId(productId)
                .sku(row.sku())
                .variantName(row.variantName())
                .unit(row.unit())
                .conversionRate(BigDecimal.ONE)
                .color(row.color())
                .size(row.size())
                .weight(BigDecimal.ZERO)
                .technicalSpec(row.technicalSpec())
                .costPrice(row.costPrice())
                .sellingPrice(row.sellingPrice())
                .warrantyPolicyId(null)
                .serialTracking(row.serialTracking())
                .status(VariantStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Cập nhật Bảng 11: product_variants khi chọn chế độ OVERWRITE_EXISTING
     */
    public void updateVariant(ProductVariant variant, ProductExcelRowDto row) {
        variant.setVariantName(row.variantName());
        variant.setUnit(row.unit());
        variant.setColor(row.color());
        variant.setSize(row.size());
        variant.setCostPrice(row.costPrice());
        variant.setSellingPrice(row.sellingPrice());
        variant.setSerialTracking(row.serialTracking());
        if (row.technicalSpec() != null && !row.technicalSpec().isEmpty()) {
            variant.setTechnicalSpec(row.technicalSpec());
        }
        variant.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Tạo danh sách Bảng 12: product_images (Hình ảnh biến thể)
     */
    public List<ProductImage> toVariantImages(
            UUID productId, UUID variantId, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProductImage> images = new ArrayList<>(imageUrls.size());
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < imageUrls.size(); i++) {
            images.add(
                    ProductImage.builder()
                            .productId(productId)
                            .variantId(variantId)
                            .imageUrl(imageUrls.get(i))
                            .isPrimary(i == 0) // Ảnh đầu tiên làm Thumbnail
                            .sortOrder(i + 1) // Thứ tự hiển thị 1, 2, 3...
                            .createdAt(now)
                            .build());
        }
        return images;
    }
}
