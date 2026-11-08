package com.aquasuite.ims.module.catalog.internal.repository;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.enums.*;
import com.aquasuite.ims.share.exception.*;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.*;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@org.springframework.stereotype.Repository
@RequiredArgsConstructor
public class BundleRepository {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final BundleConfigurationRepository configs;
    private final BundleItemRepository items;

    /** Loaded persistence data only; pricing and response assembly belong to the query service. */
    public record BundleData(
            Product product,
            BundleConfiguration configuration,
            List<BundleItem> items,
            Map<UUID, ProductVariant> variants,
            Map<UUID, Product> parents) {}

    public Product findBundleProduct(UUID id, boolean includeDeleted) {
        return products.findById(id)
                .filter(
                        p ->
                                p.getProductType() == ProductType.BUNDLE
                                        && (includeDeleted
                                                || p.getStatus() != ProductStatus.DELETED))
                .orElseThrow(() -> new AppException(CommonErrorCode.RESOURCE_NOT_FOUND));
    }

    public Map<UUID, BundleData> loadAggregates(List<Product> ps) {
        if (ps.isEmpty()) return Map.of();
        var ids = ps.stream().map(Product::getProductId).toList();
        var cs =
                configs.findAllById(ids).stream()
                        .collect(
                                Collectors.toMap(
                                        BundleConfiguration::getBundleProductId,
                                        Function.identity()));
        var its = items.findByBundleProductIdIn(ids);
        its.sort(Comparator.comparing(BundleItem::getBundleItemId));
        var itemsByBundle =
                its.stream().collect(Collectors.groupingBy(BundleItem::getBundleProductId));
        var vs =
                variants.findAllById(its.stream().map(BundleItem::getVariantId).toList()).stream()
                        .collect(
                                Collectors.toMap(
                                        ProductVariant::getVariantId, Function.identity()));
        var parents =
                products
                        .findAllById(
                                vs.values().stream().map(ProductVariant::getProductId).toList())
                        .stream()
                        .collect(Collectors.toMap(Product::getProductId, Function.identity()));
        Map<UUID, BundleData> out = new HashMap<>();
        for (var p : ps) {
            var c = cs.get(p.getProductId());
            out.put(
                    p.getProductId(),
                    new BundleData(
                            p,
                            c,
                            itemsByBundle.getOrDefault(p.getProductId(), List.of()),
                            vs,
                            parents));
        }
        return out;
    }
}
