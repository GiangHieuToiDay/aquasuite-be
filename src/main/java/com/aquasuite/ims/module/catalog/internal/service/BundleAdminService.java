package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.entity.BundleConfiguration.PublicationStatus;
import com.aquasuite.ims.module.catalog.internal.entity.enums.*;
import com.aquasuite.ims.module.catalog.internal.exception.BundleErrorCode;
import com.aquasuite.ims.module.catalog.internal.mapper.BundleCoreMapper;
import com.aquasuite.ims.module.catalog.internal.payload.*;
import com.aquasuite.ims.module.catalog.internal.repository.*;
import com.aquasuite.ims.share.exception.*;

import jakarta.validation.Validator;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BundleAdminService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final BundleConfigurationRepository configs;
    private final BundleItemRepository items;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final BundlePricingService pricing;
    private final BundleCoreMapper mapper;
    private final Clock clock;
    private final Validator validator;

    private void valid(Object request) {
        if (request == null || !validator.validate(request).isEmpty())
            throw new AppException(CommonErrorCode.VALIDATION_ERROR);
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public BundlePage<BundleCoreSummary> list(
            int page,
            int size,
            String keyword,
            ProductStatus productStatus,
            PublicationStatus publicationStatus) {
        if (page < 0
                || size < 1
                || size > 100
                || (keyword != null && keyword.length() > 255)
                || productStatus == ProductStatus.DELETED)
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        String q = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        Specification<Product> spec =
                (r, c, b) -> {
                    var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
                    predicates.add(b.equal(r.get("productType"), ProductType.BUNDLE));
                    predicates.add(b.notEqual(r.get("status"), ProductStatus.DELETED));
                    if (productStatus != null)
                        predicates.add(b.equal(r.get("status"), productStatus));
                    if (!q.isEmpty())
                        predicates.add(
                                b.or(
                                        b.gt(b.locate(b.lower(r.get("productCode")), q), 0),
                                        b.gt(b.locate(b.lower(r.get("productName")), q), 0)));
                    if (publicationStatus != null) {
                        var sub = c.subquery(UUID.class);
                        var cfg = sub.from(BundleConfiguration.class);
                        sub.select(cfg.get("bundleProductId"))
                                .where(b.equal(cfg.get("publicationStatus"), publicationStatus));
                        predicates.add(r.get("productId").in(sub));
                    }
                    return b.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
                };
        var result =
                products.findAll(
                        spec,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(
                                        Sort.Order.desc("createdAt"),
                                        Sort.Order.asc("productId"))));
        var details = details(result.getContent());
        return new BundlePage<>(
                result.getContent().stream()
                        .map(p -> mapper.summary(details.get(p.getProductId())))
                        .toList(),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public BundleCoreDetail get(UUID id) {
        return view(product(id, false));
    }

    @Transactional
    public BundleCoreDetail create(BundleCoreCreateRequest r) {
        valid(r);
        validateDefinition(r.categoryId(), r.brandId(), r.items(), r.packagePrice());
        if (products.findByProductCode(r.productCode()).isPresent())
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        var p =
                Product.builder()
                        .productCode(r.productCode())
                        .productName(r.name())
                        .categoryId(r.categoryId())
                        .brandId(r.brandId())
                        .description(r.description())
                        .productType(ProductType.BUNDLE)
                        .status(ProductStatus.ACTIVE)
                        .createdAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                        .updatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                        .build();
        products.saveAndFlush(p);
        var c = new BundleConfiguration();
        c.setBundleProductId(p.getProductId());
        c.setPackagePrice(r.packagePrice());
        c.setCreatedAt(clock.instant());
        c.setUpdatedAt(clock.instant());
        configs.saveAndFlush(c);
        replace(p.getProductId(), r.items());
        var d = view(p);
        return d;
    }

    @Transactional
    public BundleCoreDetail update(UUID id, BundleCoreUpdateRequest r) {
        valid(r);
        var c = locked(id, r.expectedVersion(), false);
        var p = product(id, false);
        if (c.getPublicationStatus() == PublicationStatus.PUBLISHED)
            throw new AppException(BundleErrorCode.INVALID_STATE);
        if (!p.getProductCode().equals(r.productCode()))
            throw new AppException(BundleErrorCode.INVALID_DEFINITION);
        validateDefinition(r.categoryId(), r.brandId(), r.items(), r.packagePrice());
        p.setProductName(r.name());
        p.setCategoryId(r.categoryId());
        p.setBrandId(r.brandId());
        p.setDescription(r.description());
        p.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        products.save(p);
        replace(id, r.items());
        c.setPackagePrice(r.packagePrice());
        touch(c);
        var d = view(p);
        return d;
    }

    @Transactional
    public BundleCoreMutationResult status(UUID id, BundleCoreStatusRequest r) {
        valid(r);
        var c = locked(id, r.expectedVersion(), false);
        var p = product(id, false);
        var target = ProductStatus.valueOf(r.productStatus().name());
        if (p.getStatus() == target) return mutation(p, c);
        p.setStatus(target);
        p.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        products.save(p);
        touch(c);
        return mutation(p, c);
    }

    @Transactional
    public BundleCoreMutationResult delete(UUID id, long expectedVersion) {
        if (expectedVersion < 0) throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        var c = locked(id, expectedVersion, true);
        var p = product(id, true);
        if (p.getStatus() == ProductStatus.DELETED) return mutation(p, c);
        p.setStatus(ProductStatus.DELETED);
        p.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        products.save(p);
        touch(c);
        return mutation(p, c);
    }

    private BundleCoreMutationResult mutation(Product p, BundleConfiguration c) {
        return new BundleCoreMutationResult(
                p.getProductId(),
                p.getStatus().name(),
                c.getPublicationStatus().name(),
                c.getVersion());
    }

    private BundleConfiguration locked(UUID id, long expected, boolean includeDeleted) {
        var c =
                configs.lock(id)
                        .orElseThrow(() -> new AppException(CommonErrorCode.RESOURCE_NOT_FOUND));
        // Read the product after acquiring the aggregate lock. Only DELETE retries may
        // inspect a tombstone; update/status must return 404 even for an old version.
        product(id, includeDeleted);
        if (!Objects.equals(c.getVersion(), expected))
            throw new AppException(BundleErrorCode.STALE_VERSION);
        return c;
    }

    private Product product(UUID id, boolean includeDeleted) {
        return products.findById(id)
                .filter(
                        p ->
                                p.getProductType() == ProductType.BUNDLE
                                        && (includeDeleted
                                                || p.getStatus() != ProductStatus.DELETED))
                .orElseThrow(() -> new AppException(CommonErrorCode.RESOURCE_NOT_FOUND));
    }

    private void touch(BundleConfiguration c) {
        Instant now = clock.instant();
        c.setUpdatedAt(now.isAfter(c.getUpdatedAt()) ? now : c.getUpdatedAt().plusNanos(1000));
        configs.saveAndFlush(c);
    }

    private void validateDefinition(
            UUID category,
            UUID brand,
            List<BundleCoreCreateRequest.Item> requested,
            BigDecimal price) {
        if (categories
                        .findById(category)
                        .filter(c -> c.getStatus() == CategoryStatus.ACTIVE)
                        .isEmpty()
                || (brand != null
                        && brands.findById(brand)
                                .filter(b -> b.getStatus() == BrandStatus.ACTIVE)
                                .isEmpty()))
            throw new AppException(BundleErrorCode.INVALID_DEFINITION);
        Set<UUID> ids =
                requested.stream()
                        .map(BundleCoreCreateRequest.Item::variantId)
                        .collect(Collectors.toSet());
        if (ids.size() != requested.size())
            throw new AppException(BundleErrorCode.INVALID_COMPONENT);
        var vs =
                variants.findAllById(ids).stream()
                        .collect(
                                Collectors.toMap(
                                        ProductVariant::getVariantId, Function.identity()));
        var ps =
                products
                        .findAllById(
                                vs.values().stream().map(ProductVariant::getProductId).toList())
                        .stream()
                        .collect(Collectors.toMap(Product::getProductId, Function.identity()));
        for (var id : ids) {
            var v = vs.get(id);
            var p = v == null ? null : ps.get(v.getProductId());
            if (v == null
                    || v.getStatus() != VariantStatus.ACTIVE
                    || p == null
                    || p.getProductType() != ProductType.NORMAL
                    || p.getStatus() != ProductStatus.ACTIVE)
                throw new AppException(BundleErrorCode.INVALID_COMPONENT);
        }
        pricing.validate(
                requested.stream()
                        .map(
                                i ->
                                        new BundlePricingService.Line(
                                                vs.get(i.variantId()).getSellingPrice(),
                                                i.quantity()))
                        .toList(),
                price);
    }

    private void replace(UUID id, List<BundleCoreCreateRequest.Item> requested) {
        var existing =
                items.findByBundleProductIdOrderByBundleItemId(id).stream()
                        .collect(Collectors.toMap(BundleItem::getVariantId, Function.identity()));
        var keep =
                requested.stream()
                        .map(BundleCoreCreateRequest.Item::variantId)
                        .collect(Collectors.toSet());
        items.deleteAll(
                existing.values().stream().filter(i -> !keep.contains(i.getVariantId())).toList());
        items.flush();
        for (var i : requested) {
            var item = existing.get(i.variantId());
            if (item == null)
                item =
                        BundleItem.builder()
                                .bundleProductId(id)
                                .variantId(i.variantId())
                                .createdAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                                .build();
            item.setQuantity(i.quantity());
            items.save(item);
        }
        items.flush();
    }

    private BundleCoreDetail view(Product p) {
        return details(List.of(p)).get(p.getProductId());
    }

    private Map<UUID, BundleCoreDetail> details(List<Product> ps) {
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
        Map<UUID, BundleCoreDetail> out = new HashMap<>();
        for (var p : ps) {
            var c = cs.get(p.getProductId());
            if (c == null)
                throw new AppException(
                        BundleErrorCode.INVALID_STATE, "Combo requires schema migration");
            out.put(
                    p.getProductId(),
                    mapper.detail(
                            p,
                            c,
                            its.stream()
                                    .filter(i -> i.getBundleProductId().equals(p.getProductId()))
                                    .toList(),
                            vs,
                            parents));
        }
        return out;
    }
}
