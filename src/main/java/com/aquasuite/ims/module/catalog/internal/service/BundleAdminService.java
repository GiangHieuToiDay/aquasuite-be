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

/**
 * Điều phối các chức năng quản trị combo và tìm SKU phục vụ màn hình cấu hình combo.
 * Luồng ghi: kiểm request → khóa/kiểm version khi sửa → kiểm definition/SKU/giá
 * → ghi Product/config/items trong cùng transaction → assembler/MapStruct tạo DTO trả controller.
 * Luồng đọc chỉ truy vấn catalog; công thức giá giao cho BundlePricingService,
 * ghép response giao cho BundleDetailAssembler. Không gọi Auth/Audit/tồn kho trong Phase 1.
 */
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
    private final BundleDetailAssembler detailAssembler;
    private final Clock clock;
    private final Validator validator;

    /**
     * Kiểm Bean Validation cả khi use case được gọi trực tiếp, không chỉ qua @Valid ở HTTP.
     */
    private void validateRequest(Object request) {
        if (request == null || !validator.validate(request).isEmpty())
            throw new AppException(CommonErrorCode.VALIDATION_ERROR);
    }

    /**
     * Lọc Product BUNDLE chưa xóa trước phân trang → đọc config/items/SKU theo lô
     * → assembler tạo detail và MapStruct rút thành summary; không thay version.
     */
    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public BundlePage<BundleCoreSummary> list(
            int page,
            int size,
            String keyword,
            ProductStatus productStatus,
            PublicationStatus publicationStatus) {
        validatePageRequest(page, size, keyword);
        if (productStatus == ProductStatus.DELETED)
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
        var details = loadDetails(result.getContent());
        return new BundlePage<>(
                result.getContent().stream()
                        .map(p -> mapper.summary(details.get(p.getProductId())))
                        .toList(),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages());
    }

    /**
     * Tìm SKU ACTIVE thuộc sản phẩm NORMAL ACTIVE, lọc trước khi phân trang.
     * Đọc tên sản phẩm theo lô rồi MapStruct tạo DTO; không trả giá vốn hoặc tồn kho.
     */
    @Transactional(readOnly = true)
    public BundlePage<BundleSkuResponse> searchSkus(int page, int size, String keyword) {
        validatePageRequest(page, size, keyword);
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        Specification<ProductVariant> specification = (variantRoot, query, builder) -> {
            // Chỉ xét SKU có sản phẩm cha NORMAL đang ACTIVE, loại trước khi phân trang.
            var eligibleProductIds = query.subquery(UUID.class);
            var productRoot = eligibleProductIds.from(Product.class);
            eligibleProductIds.select(productRoot.get("productId"))
                    .where(builder.equal(productRoot.get("productType"), ProductType.NORMAL),
                            builder.equal(productRoot.get("status"), ProductStatus.ACTIVE));
            var eligibleVariant = builder.and(
                    builder.equal(variantRoot.get("status"), VariantStatus.ACTIVE),
                    variantRoot.get("productId").in(eligibleProductIds));
            if (normalizedKeyword.isEmpty()) return eligibleVariant;

            // Tìm theo tên sản phẩm cha; locate giữ % và _ là ký tự tìm kiếm thông thường.
            var matchingProductIds = query.subquery(UUID.class);
            var matchingProduct = matchingProductIds.from(Product.class);
            matchingProductIds.select(matchingProduct.get("productId"))
                    .where(builder.gt(builder.locate(
                            builder.lower(matchingProduct.get("productName")), normalizedKeyword), 0));
            return builder.and(eligibleVariant, builder.or(
                    builder.gt(builder.locate(builder.lower(variantRoot.get("sku")), normalizedKeyword), 0),
                    builder.gt(builder.locate(builder.lower(variantRoot.get("variantName")), normalizedKeyword), 0),
                    variantRoot.get("productId").in(matchingProductIds)));
        };
        var variantsPage = variants.findAll(
                specification, PageRequest.of(page, size, Sort.by("sku", "variantId")));
        var productsById = products.findAllById(
                        variantsPage.stream().map(ProductVariant::getProductId).toList())
                .stream().collect(Collectors.toMap(Product::getProductId, Function.identity()));
        var skuResponses = variantsPage.stream()
                .map(variant -> mapper.sku(variant, productsById.get(variant.getProductId())))
                .toList();
        return new BundlePage<>(skuResponses, page, size,
                variantsPage.getTotalElements(), variantsPage.getTotalPages());
    }

    /**
     * Dùng chung giới hạn page/size/keyword cho danh sách combo và lookup SKU.
     */
    void validatePageRequest(int page, int size, String keyword) {
        if (page < 0 || size < 1 || size > 100 || (keyword != null && keyword.length() > 255))
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
    }

    /**
     * Đọc combo chưa xóa → lấy thành phần/giá hiện tại → ghép detail trong transaction chỉ đọc.
     */
    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public BundleCoreDetail get(UUID id) {
        return loadDetail(findBundleProduct(id, false));
    }

    /**
     * Kiểm request, danh mục, SKU và giá → tạo Product BUNDLE ACTIVE/config DRAFT/items.
     * Mọi ghi cùng transaction; lỗi ở bất kỳ bước nào rollback toàn bộ.
     */
    @Transactional
    public BundleCoreDetail create(BundleCoreCreateRequest payload) {
        validateRequest(payload);
        validateDefinition(payload.categoryId(), payload.brandId(), payload.items(), payload.packagePrice());
        validatePublicationDefinition(payload.validFrom(), payload.validUntil(), payload.items(), payload.componentGroups());
        if (products.findByProductCode(payload.productCode()).isPresent())
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        var product =
                Product.builder()
                        .productCode(payload.productCode())
                        .productName(payload.name())
                        .categoryId(payload.categoryId())
                        .brandId(payload.brandId())
                        .description(payload.description())
                        .productType(ProductType.BUNDLE)
                        .status(ProductStatus.ACTIVE)
                        .createdAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                        .updatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                        .build();
        products.saveAndFlush(product);
        var configuration = new BundleConfiguration();
        configuration.setBundleProductId(product.getProductId());
        configuration.setPackagePrice(payload.packagePrice());
        configurePublication(configuration, payload.validFrom(), payload.validUntil(), payload.componentGroups());
        configuration.setCreatedAt(clock.instant());
        configuration.setUpdatedAt(clock.instant());
        configs.saveAndFlush(configuration);
        replaceItems(product.getProductId(), payload.items());
        return loadDetail(product);
    }

    /**
     * Khóa cấu hình → loại combo đã xóa → kiểm expectedVersion và trạng thái publication
     * → thay definition/items, giữ mã và ID item còn dùng → tăng version và trả detail.
     */
    @Transactional
    public BundleCoreDetail update(UUID id, BundleCoreUpdateRequest payload) {
        validateRequest(payload);
        var configuration = lockConfiguration(id, payload.expectedVersion(), false);
        var product = findBundleProduct(id, false);
        if (configuration.getPublicationStatus() == PublicationStatus.PUBLISHED)
            throw new AppException(BundleErrorCode.INVALID_STATE);
        if (!product.getProductCode().equals(payload.productCode()))
            throw new AppException(BundleErrorCode.INVALID_DEFINITION);
        validateDefinition(payload.categoryId(), payload.brandId(), payload.items(), payload.packagePrice());
        validatePublicationDefinition(payload.validFrom(), payload.validUntil(), payload.items(), payload.componentGroups());
        product.setProductName(payload.name());
        product.setCategoryId(payload.categoryId());
        product.setBrandId(payload.brandId());
        product.setDescription(payload.description());
        product.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        products.save(product);
        replaceItems(id, payload.items());
        configuration.setPackagePrice(payload.packagePrice());
        configurePublication(configuration, payload.validFrom(), payload.validUntil(), payload.componentGroups());
        markConfigurationUpdated(configuration);
        return loadDetail(product);
    }

    /**
     * Khóa và kiểm version → đổi ACTIVE/INACTIVE, không đổi publication hoặc tồn.
     * Cùng trạng thái và version hiện tại là no-op; version cũ vẫn bị từ chối.
     */
    @Transactional
    public BundleCoreMutationResult status(UUID id, BundleCoreStatusRequest payload) {
        validateRequest(payload);
        var configuration = lockConfiguration(id, payload.expectedVersion(), false);
        var product = findBundleProduct(id, false);
        var target = ProductStatus.valueOf(payload.productStatus().name());
        if (product.getStatus() == target) return mapper.mutation(product, configuration);
        product.setStatus(target);
        product.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        products.save(product);
        markConfigurationUpdated(configuration);
        return mapper.mutation(product, configuration);
    }

    /**
     * Khóa và kiểm version → đặt Product DELETED, giữ mã và các item để truy vết.
     * Retry bằng version hiện tại trả tombstone không ghi lại; version cũ trả 409.
     */
    @Transactional
    public BundleCoreMutationResult delete(UUID id, long expectedVersion) {
        if (expectedVersion < 0) throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        var configuration = lockConfiguration(id, expectedVersion, true);
        var product = findBundleProduct(id, true);
        if (product.getStatus() == ProductStatus.DELETED) return mapper.mutation(product, configuration);
        product.setStatus(ProductStatus.DELETED);
        product.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        products.save(product);
        markConfigurationUpdated(configuration);
        return mapper.mutation(product, configuration);
    }

    /** Revalidates definition under the aggregate lock; never reads or reserves inventory. */
    @Transactional
    public BundleCoreDetail publish(UUID id, BundleVersionRequest payload) {
        validateRequest(payload);
        var config = lockConfiguration(id, payload.expectedVersion(), false);
        var product = findBundleProduct(id, false);
        if (product.getStatus() != ProductStatus.ACTIVE)
            throw new AppException(BundleErrorCode.INVALID_STATE);
        if (config.getPublicationStatus() == PublicationStatus.PUBLISHED) return loadDetail(product);
        var detail = loadDetail(product);
        var requested = detail.items().stream().map(i -> new BundleItemRequest(i.variantId(), i.quantity())).toList();
        validateDefinition(product.getCategoryId(), product.getBrandId(), requested, config.getPackagePrice());
        validatePublicationDefinition(config.getValidFrom(), config.getValidUntil(), requested, detail.componentGroups());
        if (config.getValidFrom() == null || !clock.instant().isBefore(config.getValidUntil()))
            throw new AppException(BundleErrorCode.INVALID_DEFINITION);
        config.setPublicationStatus(PublicationStatus.PUBLISHED);
        markConfigurationUpdated(config);
        return loadDetail(product);
    }

    /** Unpublishing remains possible for inactive/expired bundles; current-version retries are no-op. */
    @Transactional
    public BundleCoreDetail unpublish(UUID id, BundleVersionRequest payload) {
        validateRequest(payload);
        var config = lockConfiguration(id, payload.expectedVersion(), false);
        if (config.getPublicationStatus() == PublicationStatus.DRAFT)
            throw new AppException(BundleErrorCode.INVALID_STATE);
        if (config.getPublicationStatus() == PublicationStatus.PUBLISHED) {
            config.setPublicationStatus(PublicationStatus.UNPUBLISHED);
            markConfigurationUpdated(config);
        }
        return loadDetail(findBundleProduct(id, false));
    }

    private void configurePublication(BundleConfiguration config, Instant from, Instant until,
            List<BundleComponentGroupRequest> groups) {
        config.setValidFrom(from);
        config.setValidUntil(until);
        config.getComponentGroups().clear();
        groups.forEach(g -> config.getComponentGroups().add(new BundleComponentGroup(
                g.groupId(), g.defaultVariantId(), g.allowedVariantIds().toArray(UUID[]::new))));
    }

    /** Same-price whitelist is checked on save and again at publish, using current retail prices. */
    private void validatePublicationDefinition(Instant from, Instant until,
            List<BundleItemRequest> requested, List<BundleComponentGroupRequest> groups) {
        if ((from == null) != (until == null) || (from != null && !from.isBefore(until)))
            throw new AppException(BundleErrorCode.INVALID_DEFINITION);
        Set<UUID> defaults = new HashSet<>(), groupIds = new HashSet<>();
        var itemIds = requested.stream().map(BundleItemRequest::variantId).collect(Collectors.toSet());
        Set<UUID> allIds = groups.stream().flatMap(g -> g.allowedVariantIds().stream()).collect(Collectors.toSet());
        var optionVariants = variants.findAllById(allIds).stream()
                .collect(Collectors.toMap(ProductVariant::getVariantId, Function.identity()));
        var optionProducts = products.findAllById(optionVariants.values().stream().map(ProductVariant::getProductId).toList())
                .stream().collect(Collectors.toMap(Product::getProductId, Function.identity()));
        for (var group : groups) {
            if (!groupIds.add(group.groupId()) || !defaults.add(group.defaultVariantId())
                    || !itemIds.contains(group.defaultVariantId())
                    || !group.allowedVariantIds().contains(group.defaultVariantId())
                    || new HashSet<>(group.allowedVariantIds()).size() != group.allowedVariantIds().size())
                throw new AppException(BundleErrorCode.INVALID_COMPONENT);
            var base = optionVariants.get(group.defaultVariantId());
            for (var id : group.allowedVariantIds()) {
                var variant = optionVariants.get(id);
                var parent = variant == null ? null : optionProducts.get(variant.getProductId());
                if (base == null || base.getSellingPrice() == null || variant == null
                        || variant.getSellingPrice() == null || variant.getStatus() != VariantStatus.ACTIVE
                        || parent == null || parent.getStatus() != ProductStatus.ACTIVE
                        || parent.getProductType() != ProductType.NORMAL
                        || variant.getSellingPrice().compareTo(base.getSellingPrice()) != 0)
                    throw new AppException(BundleErrorCode.INVALID_COMPONENT);
            }
        }
    }

    /**
     * Khóa aggregate trước khi đọc trạng thái/version; chỉ DELETE retry được phép thấy tombstone.
     */
    private BundleConfiguration lockConfiguration(UUID id, long expected, boolean includeDeleted) {
        var c =
                configs.lock(id)
                        .orElseThrow(() -> new AppException(CommonErrorCode.RESOURCE_NOT_FOUND));
        // Read the product after acquiring the aggregate lock. Only DELETE retries may
        // inspect a tombstone; update/status must return 404 even for an old version.
        findBundleProduct(id, includeDeleted);
        if (!Objects.equals(c.getVersion(), expected))
            throw new AppException(BundleErrorCode.STALE_VERSION);
        return c;
    }

    /**
     * Chỉ nhận Product BUNDLE; mặc định ẩn DELETED để các thao tác thông thường trả 404.
     */
    private Product findBundleProduct(UUID id, boolean includeDeleted) {
        return products.findById(id)
                .filter(
                        p ->
                                p.getProductType() == ProductType.BUNDLE
                                        && (includeDeleted
                                                || p.getStatus() != ProductStatus.DELETED))
                .orElseThrow(() -> new AppException(CommonErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * Làm dirty header và flush để @Version tăng kể cả khi chỉ sửa item hoặc Product.status.
     */
    private void markConfigurationUpdated(BundleConfiguration c) {
        Instant now = clock.instant();
        c.setUpdatedAt(now.isAfter(c.getUpdatedAt()) ? now : c.getUpdatedAt().plusNanos(1000));
        configs.saveAndFlush(c);
    }

    /**
     * Kiểm danh mục/brand ACTIVE, SKU distinct NORMAL ACTIVE và giá gói trước khi ghi.
     */
    private void validateDefinition(
            UUID category,
            UUID brand,
            List<BundleItemRequest> requested,
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
                        .map(BundleItemRequest::variantId)
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

    /**
     * Thay toàn bộ thành phần; giữ ID của SKU còn lại, xóa dòng bỏ đi và thêm SKU mới trong transaction.
     */
    private void replaceItems(UUID id, List<BundleItemRequest> requested) {
        var existing =
                items.findByBundleProductIdOrderByBundleItemId(id).stream()
                        .collect(Collectors.toMap(BundleItem::getVariantId, Function.identity()));
        var keep =
                requested.stream()
                        .map(BundleItemRequest::variantId)
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

    /**
     * Dùng chung luồng đọc theo lô cho một combo để không lặp logic ghép DTO.
     */
    private BundleCoreDetail loadDetail(Product p) {
        return loadDetails(List.of(p)).get(p.getProductId());
    }

    /**
     * Đọc config/items/SKU/sản phẩm cha theo lô → assembler tạo detail cho từng combo.
     */
    Map<UUID, BundleCoreDetail> loadDetails(List<Product> ps) {
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
                    detailAssembler.detail(
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
