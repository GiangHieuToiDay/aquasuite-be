package com.aquasuite.ims.module.catalog.internal.service;

import static com.aquasuite.ims.module.catalog.internal.service.BundleDetailAssembler.isSellable;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.entity.BundleConfiguration.PublicationStatus;
import com.aquasuite.ims.module.catalog.internal.enums.*;
import com.aquasuite.ims.module.catalog.internal.error.BundleErrorCode;
import com.aquasuite.ims.module.catalog.internal.mapper.BundleCoreMapper;
import com.aquasuite.ims.module.catalog.internal.payload.req.*;
import com.aquasuite.ims.module.catalog.internal.payload.req.BundlePage;
import com.aquasuite.ims.module.catalog.internal.payload.req.BundleResponse;
import com.aquasuite.ims.module.catalog.internal.payload.req.BundleSkuResponse;
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
 * ghép response giao cho BundleDetailAssembler. JWT được kiểm ở bộ lọc HTTP dùng chung;
 * service quản trị không gọi tồn kho và chưa ghi Audit nghiệp vụ.
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
     * → assembler tạo response đầy đủ; không thay version.
     */
    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public BundlePage<BundleResponse> list(
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
                result.getContent().stream().map(p -> details.get(p.getProductId())).toList(),
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
        Specification<ProductVariant> specification =
                (variantRoot, query, builder) -> {
                    // Chỉ xét SKU có sản phẩm cha NORMAL đang ACTIVE, loại trước khi phân trang.
                    var eligibleProductIds = query.subquery(UUID.class);
                    var productRoot = eligibleProductIds.from(Product.class);
                    eligibleProductIds
                            .select(productRoot.get("productId"))
                            .where(
                                    builder.equal(
                                            productRoot.get("productType"), ProductType.NORMAL),
                                    builder.equal(productRoot.get("status"), ProductStatus.ACTIVE));
                    var eligibleVariant =
                            builder.and(
                                    builder.equal(variantRoot.get("status"), VariantStatus.ACTIVE),
                                    variantRoot.get("productId").in(eligibleProductIds));
                    if (normalizedKeyword.isEmpty()) return eligibleVariant;

                    // Tìm theo tên sản phẩm cha; locate giữ % và _ là ký tự tìm kiếm thông thường.
                    var matchingProductIds = query.subquery(UUID.class);
                    var matchingProduct = matchingProductIds.from(Product.class);
                    matchingProductIds
                            .select(matchingProduct.get("productId"))
                            .where(
                                    builder.gt(
                                            builder.locate(
                                                    builder.lower(
                                                            matchingProduct.get("productName")),
                                                    normalizedKeyword),
                                            0));
                    return builder.and(
                            eligibleVariant,
                            builder.or(
                                    builder.gt(
                                            builder.locate(
                                                    builder.lower(variantRoot.get("sku")),
                                                    normalizedKeyword),
                                            0),
                                    builder.gt(
                                            builder.locate(
                                                    builder.lower(variantRoot.get("variantName")),
                                                    normalizedKeyword),
                                            0),
                                    variantRoot.get("productId").in(matchingProductIds)));
                };
        var variantsPage =
                variants.findAll(
                        specification, PageRequest.of(page, size, Sort.by("sku", "variantId")));
        var productsById =
                products
                        .findAllById(
                                variantsPage.stream().map(ProductVariant::getProductId).toList())
                        .stream()
                        .collect(Collectors.toMap(Product::getProductId, Function.identity()));
        var skuResponses =
                variantsPage.stream()
                        .map(
                                variant ->
                                        mapper.sku(
                                                variant, productsById.get(variant.getProductId())))
                        .toList();
        return new BundlePage<>(
                skuResponses,
                page,
                size,
                variantsPage.getTotalElements(),
                variantsPage.getTotalPages());
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
    public BundleResponse get(UUID id) {
        return loadDetail(findBundleProduct(id, false));
    }

    /**
     * Kiểm request, danh mục, SKU và giá → tạo Product BUNDLE ACTIVE/config DRAFT/items.
     * Mọi ghi cùng transaction; lỗi ở bất kỳ bước nào rollback toàn bộ.
     */
    @Transactional
    public BundleResponse create(BundleCoreCreateRequest payload) {
        validateRequest(payload);
        validateDefinition(
                payload.categoryId(), payload.brandId(), payload.items(), payload.packagePrice());
        validatePublicationDefinition(
                payload.validFrom(),
                payload.validUntil(),
                payload.items(),
                payload.componentGroups());
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
                        .createdAt(clock.instant())
                        .updatedAt(clock.instant())
                        .build();
        products.saveAndFlush(product);
        var configuration = new BundleConfiguration();
        configuration.setBundleProductId(product.getProductId());
        configuration.setPackagePrice(payload.packagePrice());
        configurePublication(
                configuration,
                payload.validFrom(),
                payload.validUntil(),
                payload.componentGroups());
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
    public BundleResponse update(UUID id, BundleCoreUpdateRequest payload) {
        validateRequest(payload);
        var configuration = lockConfiguration(id, payload.expectedVersion(), false);
        var product = findBundleProduct(id, false);
        if (configuration.getPublicationStatus() == PublicationStatus.PUBLISHED)
            throw new AppException(BundleErrorCode.INVALID_STATE);
        if (!product.getProductCode().equals(payload.productCode()))
            throw new AppException(BundleErrorCode.INVALID_DEFINITION);
        validateDefinition(
                payload.categoryId(), payload.brandId(), payload.items(), payload.packagePrice());
        validatePublicationDefinition(
                payload.validFrom(),
                payload.validUntil(),
                payload.items(),
                payload.componentGroups());
        product.setProductName(payload.name());
        product.setCategoryId(payload.categoryId());
        product.setBrandId(payload.brandId());
        product.setDescription(payload.description());
        product.setUpdatedAt(clock.instant());
        products.save(product);
        replaceItems(id, payload.items());
        configuration.setPackagePrice(payload.packagePrice());
        configurePublication(
                configuration,
                payload.validFrom(),
                payload.validUntil(),
                payload.componentGroups());
        markConfigurationUpdated(configuration);
        return loadDetail(product);
    }

    /**
     * Khóa và kiểm version → đổi ACTIVE/INACTIVE, không đổi publication hoặc tồn.
     * Cùng trạng thái và version hiện tại là no-op; version cũ vẫn bị từ chối.
     */
    @Transactional
    public BundleResponse status(UUID id, BundleCoreStatusRequest payload) {
        validateRequest(payload);
        var configuration = lockConfiguration(id, payload.expectedVersion(), false);
        var product = findBundleProduct(id, false);
        var target = ProductStatus.valueOf(payload.productStatus().name());
        if (product.getStatus() == target) return loadDetail(product);
        product.setStatus(target);
        product.setUpdatedAt(clock.instant());
        products.save(product);
        markConfigurationUpdated(configuration);
        return loadDetail(product);
    }

    /**
     * Khóa và kiểm version → đặt Product DELETED, giữ mã và các item để truy vết.
     * Retry bằng version hiện tại không ghi lại; version cũ trả 409.
     */
    @Transactional
    public void delete(UUID id, long expectedVersion) {
        if (expectedVersion < 0) throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        var configuration = lockConfiguration(id, expectedVersion, true);
        var product = findBundleProduct(id, true);
        if (product.getStatus() == ProductStatus.DELETED) return;
        product.setStatus(ProductStatus.DELETED);
        product.setUpdatedAt(clock.instant());
        products.save(product);
        markConfigurationUpdated(configuration);
    }

    /**
     * Khóa cấu hình và kiểm version trước khi kiểm lại SKU, option cùng giá và thời hạn.
     * Cho phép publish trước ngày bắt đầu nhưng không cho publish cấu hình đã hết hạn.
     * Combo ACTIVE đã PUBLISHED với version hiện tại là no-op; không đọc hoặc giữ tồn.
     */
    @Transactional
    public BundleResponse publish(UUID id, BundleVersionRequest payload) {
        validateRequest(payload);
        var config = lockConfiguration(id, payload.expectedVersion(), false);
        var product = findBundleProduct(id, false);
        if (product.getStatus() != ProductStatus.ACTIVE)
            throw new AppException(BundleErrorCode.INVALID_STATE);
        if (config.getPublicationStatus() == PublicationStatus.PUBLISHED)
            return loadDetail(product);
        var detail = loadDetail(product);
        var requested =
                detail.items().stream()
                        .map(i -> new BundleItemRequest(i.variantId(), i.quantity()))
                        .toList();
        validateDefinition(
                product.getCategoryId(), product.getBrandId(), requested, config.getPackagePrice());
        validatePublicationDefinition(
                config.getValidFrom(), config.getValidUntil(), requested, detail.componentGroups());
        if (config.getValidFrom() == null || !clock.instant().isBefore(config.getValidUntil()))
            throw new AppException(BundleErrorCode.INVALID_DEFINITION);
        config.setPublicationStatus(PublicationStatus.PUBLISHED);
        markConfigurationUpdated(config);
        return loadDetail(product);
    }

    /**
     * Cho phép ngừng công bố cả combo inactive/hết hạn; DRAFT chưa công bố thì bị từ chối.
     * Retry UNPUBLISHED với version hiện tại không ghi lại; version cũ vẫn bị từ chối.
     */
    @Transactional
    public BundleResponse unpublish(UUID id, BundleVersionRequest payload) {
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

    /** Thay toàn bộ lịch và nhóm option trong cùng aggregate; giữ groupId do client gửi. */
    private void configurePublication(
            BundleConfiguration config,
            Instant from,
            Instant until,
            List<BundleComponentGroupRequest> groups) {
        config.setValidFrom(from);
        config.setValidUntil(until);
        config.getComponentGroups().clear();
        groups.forEach(
                g ->
                        config.getComponentGroups()
                                .add(
                                        new BundleComponentGroup(
                                                g.groupId(),
                                                g.defaultVariantId(),
                                                g.allowedVariantIds().toArray(UUID[]::new))));
    }

    /**
     * Kiểm whitelist khi lưu và kiểm lại khi publish bằng giá bán lẻ hiện tại.
     * Bản nháp được thiếu cả hai mốc thời gian; nếu có lịch thì phải đủ và bắt đầu trước kết thúc.
     * Mỗi SKU mặc định chỉ thuộc một nhóm, có trong items và trong chính whitelist của nhóm.
     */
    private void validatePublicationDefinition(
            Instant from,
            Instant until,
            List<BundleItemRequest> requested,
            List<BundleComponentGroupRequest> groups) {
        if ((from == null) != (until == null) || (from != null && !from.isBefore(until)))
            throw new AppException(BundleErrorCode.INVALID_DEFINITION);
        Set<UUID> defaults = new HashSet<>(), groupIds = new HashSet<>();
        var itemIds =
                requested.stream().map(BundleItemRequest::variantId).collect(Collectors.toSet());
        Set<UUID> allIds =
                groups.stream()
                        .flatMap(g -> g.allowedVariantIds().stream())
                        .collect(Collectors.toSet());
        // Đọc SKU và sản phẩm cha theo lô, tránh truy vấn riêng cho từng option.
        var optionVariants =
                variants.findAllById(allIds).stream()
                        .collect(
                                Collectors.toMap(
                                        ProductVariant::getVariantId, Function.identity()));
        var optionProducts =
                products
                        .findAllById(
                                optionVariants.values().stream()
                                        .map(ProductVariant::getProductId)
                                        .toList())
                        .stream()
                        .collect(Collectors.toMap(Product::getProductId, Function.identity()));
        for (var group : groups) {
            if (!groupIds.add(group.groupId())
                    || !defaults.add(group.defaultVariantId())
                    || !itemIds.contains(group.defaultVariantId())
                    || !group.allowedVariantIds().contains(group.defaultVariantId())
                    || new HashSet<>(group.allowedVariantIds()).size()
                            != group.allowedVariantIds().size())
                throw new AppException(BundleErrorCode.INVALID_COMPONENT);
            // compareTo so sánh giá trị tiền, không phân biệt cách biểu diễn như 100.0 và 100.00.
            var base = optionVariants.get(group.defaultVariantId());
            for (var id : group.allowedVariantIds()) {
                var variant = optionVariants.get(id);
                var parent = variant == null ? null : optionProducts.get(variant.getProductId());
                if (base == null
                        || base.getSellingPrice() == null
                        || !isSellable(variant, parent)
                        || variant.getSellingPrice() == null
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
            UUID category, UUID brand, List<BundleItemRequest> requested, BigDecimal price) {
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
                requested.stream().map(BundleItemRequest::variantId).collect(Collectors.toSet());
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
            if (!isSellable(v, p)) throw new AppException(BundleErrorCode.INVALID_COMPONENT);
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
        var keep = requested.stream().map(BundleItemRequest::variantId).collect(Collectors.toSet());
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
                                .createdAt(clock.instant())
                                .build();
            item.setQuantity(i.quantity());
            items.save(item);
        }
        items.flush();
    }

    /**
     * Dùng chung luồng đọc theo lô cho một combo để không lặp logic ghép DTO.
     */
    private BundleResponse loadDetail(Product p) {
        return loadDetails(List.of(p)).get(p.getProductId());
    }

    /**
     * Đọc config/items/SKU/sản phẩm cha theo lô → assembler tạo detail cho từng combo.
     */
    Map<UUID, BundleResponse> loadDetails(List<Product> ps) {
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
        Map<UUID, BundleResponse> out = new HashMap<>();
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
                            itemsByBundle.getOrDefault(p.getProductId(), List.of()),
                            vs,
                            parents));
        }
        return out;
    }
}
