package com.aquasuite.ims.mapper.catalog;

import com.aquasuite.ims.entity.catalog.BundleConfiguration;
import com.aquasuite.ims.entity.catalog.BundleItem;
import com.aquasuite.ims.entity.catalog.Product;
import com.aquasuite.ims.entity.catalog.ProductVariant;
import com.aquasuite.ims.dto.catalog.response.BundleResponse;
import com.aquasuite.ims.dto.catalog.response.BundleSkuResponse;
import com.aquasuite.ims.service.catalog.helper.BundlePricingService.Price;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct sinh mã chuyển dữ liệu catalog đã xử lý sang DTO HTTP của combo. Nhận entity/cấu
 * hình/kết quả giá từ service hoặc assembler, ánh xạ field và trả DTO. Không tính giá, quyết định
 * sellable hoặc truy vấn DB. MapStruct báo lỗi build khi thiếu mapping cho field đích mới;
 * implementation được sinh trong target khi compile.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BundleMapper {
    @Mapping(target = "bundleId", source = "product.productId")
    @Mapping(target = "name", source = "product.productName")
    @Mapping(target = "productStatus", source = "product.status")
    @Mapping(target = "publicationStatus", source = "configuration.publicationStatus")
    @Mapping(target = "createdAt", source = "configuration.createdAt")
    @Mapping(target = "updatedAt", source = "configuration.updatedAt")
    @Mapping(target = "linkedItemsCount", expression = "java(items.size())")
    BundleResponse toResponse(
            Product product,
            BundleConfiguration configuration,
            Price price,
            List<BundleResponse.Item> items);

    @Mapping(target = "variantId", source = "item.variantId")
    @Mapping(target = "name", source = "variant.variantName")
    BundleResponse.Item toItemResponse(BundleItem item, ProductVariant variant, boolean sellable);

    @Mapping(target = "productId", source = "variant.productId")
    BundleSkuResponse toSkuResponse(ProductVariant variant, Product product);
}
