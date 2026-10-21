package com.aquasuite.ims.module.catalog.internal.mapper;

import com.aquasuite.ims.module.catalog.internal.entity.BundleConfiguration;
import com.aquasuite.ims.module.catalog.internal.entity.BundleComponentGroup;
import com.aquasuite.ims.module.catalog.internal.entity.BundleItem;
import com.aquasuite.ims.module.catalog.internal.entity.Product;
import com.aquasuite.ims.module.catalog.internal.entity.ProductVariant;
import com.aquasuite.ims.module.catalog.internal.payload.req.BundleComponentGroupRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.BundleResponse;
import com.aquasuite.ims.module.catalog.internal.payload.req.BundleSkuResponse;
import com.aquasuite.ims.module.catalog.internal.service.BundlePricingService.Price;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct sinh mã chuyển dữ liệu catalog đã xử lý sang DTO HTTP của combo.
 * Nhận entity/cấu hình/kết quả giá từ service hoặc assembler, ánh xạ field và trả DTO.
 * Không tính giá, quyết định sellable hoặc truy vấn DB. MapStruct báo lỗi build khi thiếu
 * mapping cho field đích mới; implementation được sinh trong target khi compile.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BundleCoreMapper {
    @Mapping(target = "bundleId", source = "product.productId")
    @Mapping(target = "name", source = "product.productName")
    @Mapping(target = "productStatus", source = "product.status")
    @Mapping(target = "publicationStatus", source = "configuration.publicationStatus")
    @Mapping(target = "createdAt", source = "configuration.createdAt")
    @Mapping(target = "updatedAt", source = "configuration.updatedAt")
    @Mapping(target = "linkedItemsCount", expression = "java(items.size())")
    BundleResponse detail(
            Product product,
            BundleConfiguration configuration,
            Price price,
            List<BundleResponse.Item> items);

    /** Chuyển whitelist lưu trong cấu hình sang DTO; kiểm SKU và giá thuộc trách nhiệm service. */
    default BundleComponentGroupRequest group(BundleComponentGroup group) {
        return new BundleComponentGroupRequest(
                group.getGroupId(),
                group.getDefaultVariantId(),
                java.util.List.of(group.getAllowedVariantIds()));
    }

    @Mapping(target = "variantId", source = "item.variantId")
    @Mapping(target = "name", source = "variant.variantName")
    BundleResponse.Item item(BundleItem item, ProductVariant variant, boolean sellable);

    @Mapping(target = "productId", source = "variant.productId")
    BundleSkuResponse sku(ProductVariant variant, Product product);
}
