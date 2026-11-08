package com.aquasuite.ims.module.catalog.internal.service.impl;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.entity.BundleConfiguration.PublicationStatus;
import com.aquasuite.ims.module.catalog.internal.enums.*;
import com.aquasuite.ims.module.catalog.internal.error.BundleErrorCode;
import com.aquasuite.ims.module.catalog.internal.mapper.BundleMapper;
import com.aquasuite.ims.module.catalog.internal.payload.req.*;
import com.aquasuite.ims.module.catalog.internal.payload.res.BundleResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.BundleSkuResponse;
import com.aquasuite.ims.module.catalog.internal.repository.*;
import com.aquasuite.ims.module.catalog.internal.service.BundleDetailAssembler;
import com.aquasuite.ims.module.catalog.internal.service.BundleQueryService;
import com.aquasuite.ims.share.dto.PageResponse;
import com.aquasuite.ims.share.exception.*;
import com.aquasuite.ims.share.util.PageableUtils;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(
        readOnly = true,
        isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class BundleQueryServiceImpl implements BundleQueryService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final BundleConfigurationRepository configs;
    private final BundleRepository repository;
    private final BundleMapper mapper;
    private final BundleDetailAssembler assembler;
    private final Clock clock;

    public PageResponse<BundleResponse> getBundleList(BundlePageRequest r) {
        if (r == null) throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        return list(
                r.getPage(),
                r.getSize(),
                r.getKeyword(),
                r.getProductStatus(),
                r.getPublicationStatus());
    }

    public PageResponse<BundleResponse> searchBundles(BundlePageRequest r) {
        return getBundleList(r);
    }

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

    public PageResponse<BundleResponse> getPublishedBundles(BundlePageRequest r) {
        if (r == null || r.getProductStatus() != null || r.getPublicationStatus() != null)
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        return publishedList(r.getPage(), r.getSize(), r.getKeyword());
    }

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
