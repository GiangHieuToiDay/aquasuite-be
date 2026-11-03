package com.aquasuite.ims.module.catalog.internal.mapper;

import com.aquasuite.ims.module.catalog.internal.entity.Brand;
import com.aquasuite.ims.module.catalog.internal.entity.Category;
import com.aquasuite.ims.module.catalog.internal.entity.Product;
import com.aquasuite.ims.module.catalog.internal.entity.ProductImage;
import com.aquasuite.ims.module.catalog.internal.entity.ProductVariant;
import com.aquasuite.ims.module.catalog.internal.payload.res.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.util.List;

/**
 * MapStruct Mapper cho Product (SPU), biến thể SKU và hình ảnh trong module Catalog.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ProductMapper {

    @Mapping(target = "productId", source = "product.productId")
    @Mapping(target = "productCode", source = "product.productCode")
    @Mapping(target = "productName", source = "product.productName")
    @Mapping(target = "categoryId", source = "product.categoryId")
    @Mapping(target = "categoryName", source = "categoryName")
    @Mapping(target = "brandId", source = "product.brandId")
    @Mapping(target = "brandName", source = "brandName")
    @Mapping(target = "productType", source = "product.productType")
    @Mapping(target = "status", source = "product.status")
    @Mapping(target = "primaryImageUrl", source = "primaryImageUrl")
    @Mapping(target = "minSellingPrice", source = "minSellingPrice")
    @Mapping(target = "maxSellingPrice", source = "maxSellingPrice")
    @Mapping(target = "totalVariants", source = "totalVariants")
    @Mapping(target = "createdAt", source = "product.createdAt")
    @Mapping(target = "updatedAt", source = "product.updatedAt")
    ProductSummaryResponse toSummaryResponse(
            Product product,
            String categoryName,
            String brandName,
            String primaryImageUrl,
            BigDecimal minSellingPrice,
            BigDecimal maxSellingPrice,
            int totalVariants
    );

    @Mapping(target = "productId", source = "product.productId")
    @Mapping(target = "productCode", source = "product.productCode")
    @Mapping(target = "productName", source = "product.productName")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "brand", source = "brand")
    @Mapping(target = "description", source = "product.description")
    @Mapping(target = "specifications", source = "product.specifications")
    @Mapping(target = "productType", source = "product.productType")
    @Mapping(target = "status", source = "product.status")
    @Mapping(target = "images", source = "images")
    @Mapping(target = "variants", source = "variants")
    @Mapping(target = "createdAt", source = "product.createdAt")
    @Mapping(target = "updatedAt", source = "product.updatedAt")
    ProductDetailResponse toDetailResponse(
            Product product,
            CategoryBriefResponse category,
            BrandBriefResponse brand,
            List<ProductVariantResponse> variants,
            List<ProductImageResponse> images
    );

    ProductImageResponse toImageResponse(ProductImage img);

    @Mapping(target = "images", source = "images")
    ProductVariantResponse toVariantResponse(ProductVariant variant, List<ProductImageResponse> images);

    CategoryBriefResponse toCategoryBrief(Category category);

    BrandBriefResponse toBrandBrief(Brand brand);
}
