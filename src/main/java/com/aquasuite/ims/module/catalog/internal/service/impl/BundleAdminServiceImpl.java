package com.aquasuite.ims.module.catalog.internal.service.impl;

import static com.aquasuite.ims.module.catalog.internal.service.BundleDetailAssembler.isSellable;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.entity.BundleConfiguration.PublicationStatus;
import com.aquasuite.ims.module.catalog.internal.enums.*;
import com.aquasuite.ims.module.catalog.internal.error.BundleErrorCode;
import com.aquasuite.ims.module.catalog.internal.payload.req.*;
import com.aquasuite.ims.module.catalog.internal.payload.res.BundleResponse;
import com.aquasuite.ims.module.catalog.internal.repository.*;
import com.aquasuite.ims.module.catalog.internal.service.*;
import com.aquasuite.ims.share.exception.*;

import jakarta.validation.Validator;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Điều phối các chức năng quản trị combo và tìm SKU phục vụ màn hình cấu hình combo. Luồng ghi:
 * kiểm request → khóa/kiểm version khi sửa → kiểm definition/SKU/giá → ghi Product/config/items
 * trong cùng transaction → assembler/MapStruct tạo DTO trả controller. Luồng đọc chỉ truy vấn
 * catalog; công thức giá giao cho BundlePricingService, ghép response giao cho
 * BundleDetailAssembler. JWT được kiểm ở bộ lọc HTTP dùng chung; service quản trị không gọi tồn kho
 * và chưa ghi Audit nghiệp vụ.
 */
@Service
@RequiredArgsConstructor
public class BundleAdminServiceImpl implements BundleAdminService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final BundleConfigurationRepository configs;
    private final BundleItemRepository items;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final BundlePricingService pricing;
    private final BundleRepository repository;
    private final BundleQueryService query;
    private final Clock clock;
    private final Validator validator;

    /** Kiểm Bean Validation cả khi use case được gọi trực tiếp, không chỉ qua @Valid ở HTTP. */
    private void validateRequest(Object request) {
        if (request == null || !validator.validate(request).isEmpty())
            throw new AppException(CommonErrorCode.VALIDATION_ERROR);
    }

    /**
     * Kiểm request, danh mục, SKU và giá → tạo Product BUNDLE ACTIVE/config DRAFT/items. Mọi ghi
     * cùng transaction; lỗi ở bất kỳ bước nào rollback toàn bộ.
     */
    @Transactional
    public BundleResponse createBundle(CreateBundleRequest payload) {
        validateRequest(payload);
        validateDefinition(
                payload.categoryId(), payload.brandId(), payload.items(), payload.packagePrice());
        validatePublicationDefinition(payload.validFrom(), payload.validUntil());
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
    @Transactional
    public BundleResponse updateBundle(UUID id, UpdateBundleRequest payload) {
        validateRequest(payload);
        var configuration = lockConfiguration(id, payload.expectedVersion(), false);
        var product = findBundleProduct(id, false);
        if (configuration.getPublicationStatus() == PublicationStatus.PUBLISHED)
            throw new AppException(BundleErrorCode.INVALID_STATE);
        if (!product.getProductCode().equals(payload.productCode()))
            throw new AppException(BundleErrorCode.INVALID_DEFINITION);
        validateDefinition(
                payload.categoryId(), payload.brandId(), payload.items(), payload.packagePrice());
        validatePublicationDefinition(payload.validFrom(), payload.validUntil());
        product.setProductName(payload.name());
        product.setCategoryId(payload.categoryId());
        product.setBrandId(payload.brandId());
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
        validateDefinition(
                product.getCategoryId(), product.getBrandId(), requested, config.getPackagePrice());
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

    /** Kiểm danh mục/brand ACTIVE, SKU distinct NORMAL ACTIVE và giá gói trước khi ghi. */
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
        return query.getBundleDetails(p.getProductId());
    }

    @Override
    @Transactional
    public BundleResponse changePublicationStatus(UUID id, ChangePublicationStatusRequest request) {
        validateRequest(request);
        return request.publicationStatus() == ChangePublicationStatusRequest.Status.PUBLISHED
                ? publish(id, request.expectedVersion())
                : unpublish(id, request.expectedVersion());
    }
}
