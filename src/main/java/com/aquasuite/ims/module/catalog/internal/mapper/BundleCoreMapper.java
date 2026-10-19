package com.aquasuite.ims.module.catalog.internal.mapper;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.entity.enums.*;
import com.aquasuite.ims.module.catalog.internal.payload.*;
import com.aquasuite.ims.module.catalog.internal.service.BundlePricingService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class BundleCoreMapper {
    private final BundlePricingService pricing;

    public BundleCoreDetail detail(
            Product p,
            BundleConfiguration c,
            List<BundleItem> items,
            Map<UUID, ProductVariant> variants,
            Map<UUID, Product> parents) {
        var lines =
                items.stream()
                        .map(
                                i -> {
                                    var v = variants.get(i.getVariantId());
                                    return new BundlePricingService.Line(
                                            v == null ? null : v.getSellingPrice(),
                                            i.getQuantity());
                                })
                        .toList();
        var price = pricing.preview(lines, c.getPackagePrice());
        var views =
                items.stream()
                        .map(
                                i -> {
                                    var v = variants.get(i.getVariantId());
                                    var parent = v == null ? null : parents.get(v.getProductId());
                                    boolean sellable =
                                            v != null
                                                    && v.getStatus() == VariantStatus.ACTIVE
                                                    && parent != null
                                                    && parent.getStatus() == ProductStatus.ACTIVE
                                                    && parent.getProductType()
                                                            == ProductType.NORMAL;
                                    return new BundleCoreDetail.Item(
                                            i.getBundleItemId(),
                                            i.getVariantId(),
                                            v == null ? null : v.getSku(),
                                            v == null ? null : v.getVariantName(),
                                            i.getQuantity(),
                                            v == null ? null : v.getSellingPrice(),
                                            sellable);
                                })
                        .toList();
        return new BundleCoreDetail(
                p.getProductId(),
                p.getProductCode(),
                p.getProductName(),
                p.getStatus().name(),
                c.getPublicationStatus().name(),
                c.getVersion(),
                items.size(),
                price.originalTotal(),
                c.getPackagePrice(),
                price.savingAmount(),
                price.discountPercent(),
                price.priceIssue(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                p.getCategoryId(),
                p.getBrandId(),
                p.getDescription(),
                views);
    }

    public BundleCoreSummary summary(BundleCoreDetail d) {
        return new BundleCoreSummary(
                d.bundleId(),
                d.productCode(),
                d.name(),
                d.productStatus(),
                d.publicationStatus(),
                d.version(),
                d.linkedItemsCount(),
                d.originalTotal(),
                d.packagePrice(),
                d.savingAmount(),
                d.discountPercent(),
                d.priceIssue(),
                d.createdAt(),
                d.updatedAt());
    }
}
