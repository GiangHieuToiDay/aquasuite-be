package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.entity.enums.*;
import com.aquasuite.ims.module.catalog.internal.exception.BundleErrorCode;
import com.aquasuite.ims.module.catalog.internal.mapper.BundleCoreMapper;
import com.aquasuite.ims.module.catalog.internal.payload.*;
import com.aquasuite.ims.module.catalog.internal.repository.*;
import com.aquasuite.ims.module.inventory.api.InventoryAvailabilityApi;
import com.aquasuite.ims.share.exception.*;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Authenticated storefront reads and selection preview. Uses catalog pricing and the Inventory read contract only. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class BundleStorefrontService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final BundleConfigurationRepository configs;
    private final BundleAdminService admin;
    private final BundleCoreMapper mapper;
    private final BundlePricingService pricing;
    private final InventoryAvailabilityApi inventory;
    private final Validator validator;
    private final Clock clock;

    public BundlePage<BundleCoreSummary> list(int page, int size, String keyword) {
        admin.validatePageRequest(page, size, keyword);
        Instant now = clock.instant();
        String term = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        Specification<Product> visible = (r, query, b) -> {
            var sub = query.subquery(UUID.class);
            var c = sub.from(BundleConfiguration.class);
            sub.select(c.get("bundleProductId")).where(
                    b.equal(c.get("publicationStatus"), BundleConfiguration.PublicationStatus.PUBLISHED),
                    b.lessThanOrEqualTo(c.get("validFrom"), now), b.greaterThan(c.get("validUntil"), now));
            var predicate = b.and(b.equal(r.get("productType"), ProductType.BUNDLE),
                    b.equal(r.get("status"), ProductStatus.ACTIVE), r.get("productId").in(sub));
            if (term.isEmpty()) return predicate;
            return b.and(predicate, b.or(b.gt(b.locate(b.lower(r.get("productCode")), term), 0),
                    b.gt(b.locate(b.lower(r.get("productName")), term), 0)));
        };
        var result = products.findAll(visible, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("productId"))));
        var details = admin.loadDetails(result.getContent());
        return new BundlePage<>(result.stream().map(p -> mapper.summary(details.get(p.getProductId()))).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    public BundleCoreDetail get(UUID id) {
        return visibleDetail(id, clock.instant());
    }

    private BundleCoreDetail visibleDetail(UUID id, Instant now) {
        var config = configs.findById(id).orElseThrow(() -> new AppException(CommonErrorCode.RESOURCE_NOT_FOUND));
        if (config.getPublicationStatus() != BundleConfiguration.PublicationStatus.PUBLISHED
                || config.getValidFrom() == null || config.getValidUntil() == null
                || now.isBefore(config.getValidFrom()) || !now.isBefore(config.getValidUntil()))
            throw new AppException(CommonErrorCode.RESOURCE_NOT_FOUND);
        var detail = admin.get(id);
        if (!ProductStatus.ACTIVE.name().equals(detail.productStatus()))
            throw new AppException(CommonErrorCode.RESOURCE_NOT_FOUND);
        return detail;
    }

    /** Resolve defaults/whitelist, recheck current option prices, then batch-read stock without writing it. */
    public BundleEvaluation evaluate(UUID id, BundleEvaluationRequest request) {
        if (request == null || !validator.validate(request).isEmpty())
            throw new AppException(CommonErrorCode.VALIDATION_ERROR);
        Instant now = clock.instant();
        var detail = visibleDetail(id, now);
        if (request.expectedVersion() != null && !request.expectedVersion().equals(detail.version()))
            throw new AppException(BundleErrorCode.STALE_VERSION);
        var groups = detail.componentGroups().stream()
                .collect(Collectors.toMap(BundleComponentGroupRequest::groupId, Function.identity()));
        Map<UUID, UUID> replacements = new HashMap<>();
        Set<UUID> selectedGroups = new HashSet<>();
        for (var selection : request.selections()) {
            var group = groups.get(selection.groupId());
            if (group == null || !selectedGroups.add(selection.groupId())
                    || !group.allowedVariantIds().contains(selection.variantId()))
                throw new AppException(BundleErrorCode.INVALID_COMPONENT);
            replacements.put(group.defaultVariantId(), selection.variantId());
        }
        var selectedIds = detail.items().stream()
                .map(i -> replacements.getOrDefault(i.variantId(), i.variantId())).toList();
        if (new HashSet<>(selectedIds).size() != selectedIds.size())
            throw new AppException(BundleErrorCode.INVALID_COMPONENT);
        var current = variants.findAllById(selectedIds).stream()
                .collect(Collectors.toMap(ProductVariant::getVariantId, Function.identity()));
        var parents = products.findAllById(current.values().stream().map(ProductVariant::getProductId).toList())
                .stream().collect(Collectors.toMap(Product::getProductId, Function.identity()));
        List<BundlePricingService.Line> lines = new ArrayList<>();
        boolean sellable = true;
        for (int index = 0; index < detail.items().size(); index++) {
            var item = detail.items().get(index);
            var variant = current.get(selectedIds.get(index));
            var parent = variant == null ? null : parents.get(variant.getProductId());
            boolean componentSellable = variant != null && variant.getStatus() == VariantStatus.ACTIVE
                    && parent != null && parent.getStatus() == ProductStatus.ACTIVE
                    && parent.getProductType() == ProductType.NORMAL;
            sellable &= componentSellable;
            if (!selectedIds.get(index).equals(item.variantId()) && (!componentSellable || variant == null
                    || variant.getSellingPrice() == null || item.sellingPrice() == null
                    || variant.getSellingPrice().compareTo(item.sellingPrice()) != 0))
                throw new AppException(BundleErrorCode.INVALID_COMPONENT);
            lines.add(new BundlePricingService.Line(variant == null ? null : variant.getSellingPrice(), item.quantity()));
        }
        var price = pricing.preview(lines, detail.packagePrice());
        BigDecimal multiplier = BigDecimal.valueOf(request.quantity());
        BigDecimal total = detail.packagePrice().multiply(multiplier);
        BigDecimal original = price.originalTotal() == null ? null : price.originalTotal().multiply(multiplier);
        if (total.compareTo(BundlePricingService.MAX) > 0 || (original != null && original.compareTo(BundlePricingService.MAX) > 0))
            throw new AppException(BundleErrorCode.INVALID_DEFINITION);
        var stock = readStock(id, selectedIds);
        boolean unknown = false;
        List<UUID> missing = new ArrayList<>();
        List<BundleEvaluation.Item> itemViews = new ArrayList<>();
        for (int index = 0; index < selectedIds.size(); index++) {
            var selected = selectedIds.get(index);
            var variant = current.get(selected);
            int required = Math.multiplyExact(detail.items().get(index).quantity(), request.quantity());
            BigDecimal available = stock.get(selected);
            unknown |= available == null;
            if (available != null && available.compareTo(BigDecimal.valueOf(required)) < 0) missing.add(selected);
            itemViews.add(new BundleEvaluation.Item(selected, variant == null ? null : variant.getSku(),
                    variant == null ? null : variant.getVariantName(), required, available));
        }
        String availability = unknown ? "UNKNOWN" : missing.isEmpty() ? "IN_STOCK" : "INSUFFICIENT";
        String issue = price.priceIssue() != null ? price.priceIssue() : sellable ? null : "COMPONENT_UNAVAILABLE";
        return new BundleEvaluation(id, detail.version(), now, request.quantity(), detail.packagePrice(), total,
                original, issue == null ? price.savingAmount().multiply(multiplier) : null,
                issue == null ? price.discountPercent() : null, issue, availability,
                issue == null && availability.equals("IN_STOCK"), List.copyOf(missing), List.copyOf(itemViews));
    }

    private Map<UUID, BigDecimal> readStock(UUID id, List<UUID> selectedIds) {
        try {
            var result = inventory.availableQuantities(selectedIds);
            return result == null ? Map.of() : result;
        } catch (RuntimeException failure) {
            log.warn("Inventory preview unavailable for bundle {} ({})", id, failure.getClass().getSimpleName());
            return Map.of();
        }
    }
}
