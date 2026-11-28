package com.aquasuite.ims.service.catalog.impl;
import com.aquasuite.ims.common.PageResponse;
import com.aquasuite.ims.dto.catalog.request.*;
import com.aquasuite.ims.dto.catalog.response.BundleResponse;
import com.aquasuite.ims.dto.catalog.response.BundleSkuResponse;
import com.aquasuite.ims.entity.catalog.*;
import com.aquasuite.ims.entity.catalog.BundleConfiguration.PublicationStatus;
import com.aquasuite.ims.enums.catalog.*;
import com.aquasuite.ims.exception.*;
import com.aquasuite.ims.exception.catalog.BundleErrorCode;
import com.aquasuite.ims.mapper.catalog.BundleMapper;
import com.aquasuite.ims.repository.catalog.*;
import com.aquasuite.ims.service.catalog.*;
import com.aquasuite.ims.service.catalog.BundleService;
import com.aquasuite.ims.service.catalog.helper.BundleDetailAssembler;
import com.aquasuite.ims.service.catalog.helper.BundlePricingService;
import com.aquasuite.ims.util.PageableUtils;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.aquasuite.ims.service.catalog.helper.BundleDetailAssembler.isSellable;

@Service
@RequiredArgsConstructor
public class BundleServiceImpl implements BundleService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final BundleConfigurationRepository configs;
    private final BundleItemRepository items;
    private final CategoryRepository categories;
    private final BundlePricingService pricing;
    private final BundleRepository repository;
    private final Clock clock;
    private final Validator validator;
    private final BundleMapper mapper;
    private final BundleDetailAssembler assembler;

    /** Kiểm Bean Validation cả khi use case được gọi trực tiếp, không chỉ qua @Valid ở HTTP. */
    private void validateRequest(Object request) {
        if (request == null || !validator.validate(request).isEmpty())
            throw new AppException(CommonErrorCode.VALIDATION_ERROR);
    }

    /**
     * Kiểm request, danh mục, SKU và giá → tạo Product BUNDLE ACTIVE/config DRAFT/items. Mọi ghi
     * cùng transaction; lỗi ở bất kỳ bước nào rollback toàn bộ.
     */
    @Override
    @Transactional
    public BundleResponse createBundle(CreateBundleRequest payload) {
        validateRequest(payload);
        validateDefinition(payload.items(), payload.packagePrice());
        validatePublicationDefinition(payload.validFrom(), payload.validUntil());
        if (products.findByProductCode(payload.productCode()).isPresent())
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        var product =
                Product.builder()
                        .productCode(payload.productCode())
                        .productName(payload.name())
                        .categoryId(resolveComboCategoryId())
                        .brandId(null)
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
        configurePublication(configuration, payload.validFrom(), payload.validUntil());
        configuration.setCreatedAt(clock.instant());
        configuration.setUpdatedAt(clock.instant());
        configs.saveAndFlush(configuration);
        replaceItems(product.getProductId(), payload.items());
        return loadDetail(product);
    }

    /**
     * Khóa cấu hình → loại combo đã xóa → kiểm expectedVersion và trạng thái publication → thay
     * definition/items, giữ mã và ID item còn dùng → tăng version và trả detail.
     */
    @Override
    @Transactional
    public BundleResponse updateBundle(UUID id, UpdateBundleRequest payload) {
        validateRequest(payload);
        var configuration = lockConfiguration(id, payload.expectedVersion(), false);
        var product = findBundleProduct(id, false);
        if (configuration.getPublicationStatus() == PublicationStatus.PUBLISHED)
            throw new AppException(BundleErrorCode.INVALID_STATE);
        if (!product.getProductCode().equals(payload.productCode()))
            throw new AppException(BundleErrorCode.INVALID_DEFINITION);
        validateDefinition(payload.items(), payload.packagePrice());
        validatePublicationDefinition(payload.validFrom(), payload.validUntil());
        product.setProductName(payload.name());
        // Classification is server-owned; retain existing links when editing.
        product.setDescription(payload.description());
        product.setUpdatedAt(clock.instant());
        products.save(product);
        replaceItems(id, payload.items());
        configuration.setPackagePrice(payload.packagePrice());
        configurePublication(configuration, payload.validFrom(), payload.validUntil());
        markConfigurationUpdated(configuration);
        return loadDetail(product);
    }

    /**
     * Khóa và kiểm version → đổi ACTIVE/INACTIVE, không đổi publication hoặc tồn. Cùng trạng thái
     * và version hiện tại là no-op; version cũ vẫn bị từ chối.
     */
    @Override
    @Transactional
    public BundleResponse changeProductStatus(UUID id, ChangeBundleStatusRequest payload) {
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
     * Khóa và kiểm version → đặt Product DELETED, giữ mã và các item để truy vết. Retry bằng
     * version hiện tại không ghi lại; version cũ trả 409.
     */
    @Override
    @Transactional
    public void deleteBundle(UUID id, long expectedVersion) {
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
     * Khóa cấu hình và kiểm version trước khi kiểm lại SKU, giá và thời hạn. Cho phép publish trước
     * ngày bắt đầu nhưng không cho publish cấu hình đã hết hạn. Combo ACTIVE đã PUBLISHED với
     * version hiện tại là no-op; không đọc hoặc giữ tồn.
     */
    private BundleResponse publish(UUID id, long expectedVersion) {
        var config = lockConfiguration(id, expectedVersion, false);
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
        validateDefinition(requested, config.getPackagePrice());
        validatePublicationDefinition(config.getValidFrom(), config.getValidUntil());
        if (config.getValidFrom() == null || !clock.instant().isBefore(config.getValidUntil()))
            throw new AppException(BundleErrorCode.INVALID_DEFINITION);
        config.setPublicationStatus(PublicationStatus.PUBLISHED);
        markConfigurationUpdated(config);
        return loadDetail(product);
    }

    /**
     * Cho phép ngừng công bố cả combo inactive/hết hạn; DRAFT chưa công bố thì bị từ chối. Retry
     * UNPUBLISHED với version hiện tại không ghi lại; version cũ vẫn bị từ chối.
     */
    private BundleResponse unpublish(UUID id, long expectedVersion) {
        var config = lockConfiguration(id, expectedVersion, false);
        if (config.getPublicationStatus() == PublicationStatus.DRAFT)
            throw new AppException(BundleErrorCode.INVALID_STATE);
        if (config.getPublicationStatus() == PublicationStatus.PUBLISHED) {
            config.setPublicationStatus(PublicationStatus.UNPUBLISHED);
            markConfigurationUpdated(config);
        }
        return loadDetail(findBundleProduct(id, false));
    }

    /** Cập nhật lịch; không sửa dữ liệu option lịch sử. */
    private void configurePublication(BundleConfiguration config, Instant from, Instant until) {
        config.setValidFrom(from);
        config.setValidUntil(until);
        // Legacy option rows remain untouched; fixed item IDs are the only definition used.
    }

    /**
     * Kiểm cặp thời gian trước khi lưu hoặc publish. Bản nháp được thiếu cả hai mốc thời gian; nếu
     * có lịch thì phải đủ và bắt đầu trước kết thúc.
     */
    private void validatePublicationDefinition(Instant from, Instant until) {
        if ((from == null) != (until == null) || (from != null && !from.isBefore(until)))
            throw new AppException(BundleErrorCode.INVALID_DEFINITION);
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

    /** Chỉ nhận Product BUNDLE; mặc định ẩn DELETED để các thao tác thông thường trả 404. */
    private Product findBundleProduct(UUID id, boolean includeDeleted) {
        return repository.findBundleProduct(id, includeDeleted);
    }

    /** Làm dirty header và flush để @Version tăng kể cả khi chỉ sửa item hoặc Product.status. */
    private void markConfigurationUpdated(BundleConfiguration c) {
        Instant now = clock.instant();
        c.setUpdatedAt(now.isAfter(c.getUpdatedAt()) ? now : c.getUpdatedAt().plusNanos(1000));
        configs.saveAndFlush(c);
    }

    /** Category creation belongs to CatalogDataInitializer. */
    private UUID resolveComboCategoryId() {
        var value = categories.findByCategoryCodeIgnoreCase("709")
                .orElseThrow(() -> new AppException(BundleErrorCode.INVALID_DEFINITION));
        if (value.getStatus() != CategoryStatus.ACTIVE)
            throw new AppException(BundleErrorCode.INVALID_DEFINITION);
        return value.getCategoryId();
    }

    /** Validate distinct active NORMAL SKUs and price; classification is server-owned. */
    private void validateDefinition(List<BundleItemRequest> requested, BigDecimal price) {
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
     * Thay toàn bộ thành phần; giữ ID của SKU còn lại, xóa dòng bỏ đi và thêm SKU mới trong
     * transaction.
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

    /** Dùng chung luồng đọc theo lô cho một combo để không lặp logic ghép DTO. */
    private BundleResponse loadDetail(Product p) {
        return getBundleDetails(p.getProductId());
    }
    @Override
    @Transactional
    public BundleResponse changePublicationStatus(UUID id, ChangePublicationStatusRequest request) {
        validateRequest(request);
        return request.publicationStatus() == ChangePublicationStatusRequest.Status.PUBLISHED
                ? publish(id, request.expectedVersion())
                : unpublish(id, request.expectedVersion());
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResponse<BundleResponse> getBundleList(BundlePageRequest r) {
        if (r == null) throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        return list(
                r.getPage(),
                r.getSize(),
                r.getKeyword(),
                r.getProductStatus(),
                r.getPublicationStatus());
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResponse<BundleResponse> searchBundles(BundlePageRequest r) {
        return getBundleList(r);
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public BundleResponse getBundleDetails(UUID id) {
        var product = repository.findBundleProduct(id, false);
        return loadDetails(List.of(product)).get(id);
    }

    private Map<UUID, BundleResponse> loadDetails(List<Product> products) {
        Map<UUID, BundleResponse> result = new HashMap<>();
        repository
                .loadAggregates(products)
                .forEach(
                        (id, data) -> {
                            if (data.configuration() == null)
                                throw new AppException(
                                        BundleErrorCode.INVALID_STATE,
                                        "Combo requires schema migration");
                            result.put(
                                    id,
                                    assembler.detail(
                                            data.product(),
                                            data.configuration(),
                                            data.items(),
                                            data.variants(),
                                            data.parents()));
                        });
        return result;
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResponse<BundleResponse> getPublishedBundles(BundlePageRequest r) {
        if (r == null || r.getProductStatus() != null || r.getPublicationStatus() != null)
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        return publishedList(r.getPage(), r.getSize(), r.getKeyword());
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public BundleResponse getPublishedBundleDetails(UUID id) {
        return visibleDetail(id, clock.instant());
    }

    private PageResponse<BundleResponse> list(
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
                        PageableUtils.createPageable(
                                page,
                                size,
                                Sort.by(
                                        Sort.Order.desc("createdAt"),
                                        Sort.Order.asc("productId"))));
        var details = loadDetails(result.getContent());
        return PageResponse.from(result, p -> details.get(p.getProductId()));
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResponse<BundleSkuResponse> searchSkus(int page, int size, String keyword) {
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
                        specification,
                        PageableUtils.createPageable(page, size, Sort.by("sku", "variantId")));
        var productsById =
                products
                        .findAllById(
                                variantsPage.stream().map(ProductVariant::getProductId).toList())
                        .stream()
                        .collect(Collectors.toMap(Product::getProductId, Function.identity()));
        return PageResponse.from(
                variantsPage,
                variant -> mapper.toSkuResponse(variant, productsById.get(variant.getProductId())));
    }

    private PageResponse<BundleResponse> publishedList(int page, int size, String keyword) {
        validatePageRequest(page, size, keyword);
        Instant now = clock.instant();
        String term = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        // Lọc trạng thái và thời hạn ngay trong truy vấn để tổng số bản ghi/phân trang chính xác.
        Specification<Product> visible =
                (r, query, b) -> {
                    var sub = query.subquery(UUID.class);
                    var c = sub.from(BundleConfiguration.class);
                    sub.select(c.get("bundleProductId"))
                            .where(
                                    b.equal(
                                            c.get("publicationStatus"),
                                            BundleConfiguration.PublicationStatus.PUBLISHED),
                                    b.lessThanOrEqualTo(c.get("validFrom"), now),
                                    b.greaterThan(c.get("validUntil"), now));
                    var predicate =
                            b.and(
                                    b.equal(r.get("productType"), ProductType.BUNDLE),
                                    b.equal(r.get("status"), ProductStatus.ACTIVE),
                                    r.get("productId").in(sub));
                    if (term.isEmpty()) return predicate;
                    return b.and(
                            predicate,
                            b.or(
                                    b.gt(b.locate(b.lower(r.get("productCode")), term), 0),
                                    b.gt(b.locate(b.lower(r.get("productName")), term), 0)));
                };
        // ID làm tiêu chí phụ để các combo có cùng thời điểm tạo vẫn có thứ tự ổn định.
        var result =
                products.findAll(
                        visible,
                        PageableUtils.createPageable(
                                page,
                                size,
                                Sort.by(
                                        Sort.Order.desc("createdAt"),
                                        Sort.Order.asc("productId"))));
        var details = loadDetails(result.getContent());
        return PageResponse.from(result, p -> details.get(p.getProductId()));
    }

    private BundleResponse visibleDetail(UUID id, Instant now) {
        var config =
                configs.findById(id)
                        .orElseThrow(() -> new AppException(CommonErrorCode.RESOURCE_NOT_FOUND));
        if (config.getPublicationStatus() != BundleConfiguration.PublicationStatus.PUBLISHED
                || config.getValidFrom() == null
                || config.getValidUntil() == null
                || now.isBefore(config.getValidFrom())
                || !now.isBefore(config.getValidUntil()))
            throw new AppException(CommonErrorCode.RESOURCE_NOT_FOUND);
        var detail = getBundleDetails(id);
        if (!ProductStatus.ACTIVE.name().equals(detail.productStatus()))
            throw new AppException(CommonErrorCode.RESOURCE_NOT_FOUND);
        return detail;
    }

    void validatePageRequest(int page, int size, String keyword) {
        if (page < 0 || size < 1 || size > 100 || (keyword != null && keyword.length() > 255))
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
    }
}
