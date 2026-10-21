package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.entity.enums.*;
import com.aquasuite.ims.module.catalog.internal.mapper.BundleCoreMapper;
import com.aquasuite.ims.module.catalog.internal.payload.bundle.response.BundleResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ghép dữ liệu catalog đã được service đọc thành chi tiết combo để trả cho client.
 * Luồng: nối item với SKU/sản phẩm cha → xác định sellable → tính preview giá → MapStruct.
 * Giữ dòng item ngay cả khi SKU thiếu/inactive để người quản lý sửa; không tự truy vấn DB
 * hoặc ghi dữ liệu. Đây là helper tạo response, không phải một use case CRUD riêng.
 */
@Component
@RequiredArgsConstructor
public class BundleDetailAssembler {
    private final BundlePricingService pricing;
    private final BundleCoreMapper mapper;

    /**
     * Ghép item/SKU theo ID, tính sellable và preview giá rồi truyền dữ liệu đã xử lý cho MapStruct.
     */
    public BundleResponse detail(
            Product product,
            BundleConfiguration configuration,
            List<BundleItem> items,
            Map<UUID, ProductVariant> variants,
            Map<UUID, Product> parents) {
        var priceLines = new ArrayList<BundlePricingService.Line>();
        var itemViews = new ArrayList<BundleResponse.Item>();
        for (BundleItem item : items) {
            ProductVariant variant = variants.get(item.getVariantId());
            Product parent = variant == null ? null : parents.get(variant.getProductId());
            priceLines.add(
                    new BundlePricingService.Line(
                            variant == null ? null : variant.getSellingPrice(),
                            item.getQuantity()));
            itemViews.add(mapper.item(item, variant, isSellable(variant, parent)));
        }
        var price = pricing.preview(priceLines, configuration.getPackagePrice());
        return mapper.detail(product, configuration, price, itemViews);
    }

    /**
     * SKU và sản phẩm cha phải ACTIVE, cha phải NORMAL; đây không phải kiểm tra tồn kho.
     */
    static boolean isSellable(ProductVariant variant, Product parent) {
        return variant != null
                && variant.getStatus() == VariantStatus.ACTIVE
                && parent != null
                && parent.getStatus() == ProductStatus.ACTIVE
                && parent.getProductType() == ProductType.NORMAL;
    }
}
