package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.entity.enums.*;
import com.aquasuite.ims.module.catalog.internal.payload.*;
import com.aquasuite.ims.module.catalog.internal.repository.*;
import com.aquasuite.ims.share.exception.*;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BundleSkuQueryService {
    private final ProductVariantRepository variants;
    private final ProductRepository products;

    @Transactional(readOnly = true)
    public BundlePage<BundleSkuResponse> search(int page, int size, String keyword) {
        if (page < 0 || size < 1 || size > 100 || (keyword != null && keyword.length() > 255))
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        String q = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        Specification<ProductVariant> spec =
                (r, c, b) -> {
                    var parent = c.subquery(UUID.class);
                    var p = parent.from(Product.class);
                    var eligible =
                            b.and(
                                    b.equal(p.get("productType"), ProductType.NORMAL),
                                    b.equal(p.get("status"), ProductStatus.ACTIVE));
                    parent.select(p.get("productId")).where(eligible);
                    var base =
                            b.and(
                                    b.equal(r.get("status"), VariantStatus.ACTIVE),
                                    r.get("productId").in(parent));
                    if (q.isEmpty()) return base;
                    var names = c.subquery(UUID.class);
                    var n = names.from(Product.class);
                    names.select(n.get("productId"))
                            .where(b.gt(b.locate(b.lower(n.get("productName")), q), 0));
                    return b.and(
                            base,
                            b.or(
                                    b.gt(b.locate(b.lower(r.get("sku")), q), 0),
                                    b.gt(b.locate(b.lower(r.get("variantName")), q), 0),
                                    r.get("productId").in(names)));
                };
        var rows = variants.findAll(spec, PageRequest.of(page, size, Sort.by("sku", "variantId")));
        var ps =
                products
                        .findAllById(rows.stream().map(ProductVariant::getProductId).toList())
                        .stream()
                        .collect(Collectors.toMap(Product::getProductId, Function.identity()));
        return new BundlePage<>(
                rows.stream()
                        .map(
                                v ->
                                        new BundleSkuResponse(
                                                v.getVariantId(),
                                                v.getProductId(),
                                                v.getSku(),
                                                ps.get(v.getProductId()).getProductName(),
                                                v.getVariantName(),
                                                v.getUnit(),
                                                v.getColor(),
                                                v.getSize(),
                                                v.getSellingPrice()))
                        .toList(),
                page,
                size,
                rows.getTotalElements(),
                rows.getTotalPages());
    }
}
