package com.aquasuite.ims.module.catalog.internal.service;

import static com.aquasuite.ims.module.catalog.internal.service.BundleDetailAssembler.isSellable;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.error.BundleErrorCode;
import com.aquasuite.ims.module.catalog.internal.payload.req.BundleEvaluationRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.BundleEvaluationResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.BundleResponse;
import com.aquasuite.ims.module.catalog.internal.repository.*;
import com.aquasuite.ims.share.exception.*;

import jakarta.validation.Validator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class BundleEvaluationService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final BundleQueryService query;
    private final BundlePricingService pricing;
    private final BundleInventoryReader inventory;
    private final Validator validator;
    private final Clock clock;

    public BundleEvaluationResponse checkAvailability(UUID id, BundleEvaluationRequest request) {
        if (request == null || !validator.validate(request).isEmpty())
            throw new AppException(CommonErrorCode.VALIDATION_ERROR);
        Instant now = clock.instant();
        var detail = query.getPublishedBundleDetails(id);
        if (request.expectedVersion() != null
                && !request.expectedVersion().equals(detail.version()))
            throw new AppException(BundleErrorCode.STALE_VERSION);
        var selectedIds = detail.items().stream().map(BundleResponse.Item::variantId).toList();
        var current =
                variants.findAllById(selectedIds).stream()
                        .collect(
                                Collectors.toMap(
                                        ProductVariant::getVariantId, Function.identity()));
        var parents =
                products
                        .findAllById(
                                current.values().stream()
                                        .map(ProductVariant::getProductId)
                                        .toList())
                        .stream()
                        .collect(Collectors.toMap(Product::getProductId, Function.identity()));
        List<BundlePricingService.Line> lines = new ArrayList<>();
        boolean sellable = true;
        for (int index = 0; index < detail.items().size(); index++) {
            var item = detail.items().get(index);
            var variant = current.get(selectedIds.get(index));
            var parent = variant == null ? null : parents.get(variant.getProductId());
            boolean componentSellable = isSellable(variant, parent);
            sellable &= componentSellable;
            lines.add(
                    new BundlePricingService.Line(
                            variant == null ? null : variant.getSellingPrice(), item.quantity()));
        }
        var price = pricing.preview(lines, detail.packagePrice());
        // Giá cấu hình là cho một combo; tiền và nhu cầu tồn phải nhân số combo được yêu cầu.
        BigDecimal multiplier = BigDecimal.valueOf(request.quantity());
        BigDecimal total = detail.packagePrice().multiply(multiplier);
        BigDecimal original =
                price.originalTotal() == null ? null : price.originalTotal().multiply(multiplier);
        if (total.compareTo(BundlePricingService.MAX) > 0
                || (original != null && original.compareTo(BundlePricingService.MAX) > 0))
            throw new AppException(BundleErrorCode.INVALID_DEFINITION);
        var stock = readStock(id, selectedIds);
        boolean stockAvailable = true;
        List<UUID> missing = new ArrayList<>();
        List<BundleEvaluationResponse.Item> itemViews = new ArrayList<>();
        for (int index = 0; index < selectedIds.size(); index++) {
            var selected = selectedIds.get(index);
            var variant = current.get(selected);
            int required =
                    Math.multiplyExact(detail.items().get(index).quantity(), request.quantity());
            BigDecimal available = stock.get(selected);
            stockAvailable &=
                    available != null && available.compareTo(BigDecimal.valueOf(required)) >= 0;
            if (available != null && available.compareTo(BigDecimal.valueOf(required)) < 0)
                missing.add(selected);
            itemViews.add(
                    new BundleEvaluationResponse.Item(
                            selected,
                            variant == null ? null : variant.getSku(),
                            variant == null ? null : variant.getVariantName(),
                            required,
                            available));
        }
        // Chỉ báo khả dụng khi tất cả SKU có đủ số lượng; không suy diễn số tồn chưa đọc được.
        String availability = stockAvailable ? "AVAILABLE" : "UNAVAILABLE";
        String issue =
                price.priceIssue() != null
                        ? price.priceIssue()
                        : sellable ? null : "COMPONENT_UNAVAILABLE";
        return new BundleEvaluationResponse(
                id,
                detail.version(),
                now,
                request.quantity(),
                detail.packagePrice(),
                total,
                original,
                issue == null ? price.savingAmount().multiply(multiplier) : null,
                issue == null ? price.discountPercent() : null,
                issue,
                availability,
                issue == null && availability.equals("AVAILABLE"),
                List.copyOf(missing),
                List.copyOf(itemViews));
    }

    private Map<UUID, BigDecimal> readStock(UUID id, List<UUID> selectedIds) {
        try {
            var summaries = inventory.read(selectedIds);
            if (summaries == null) return Map.of();
            Map<UUID, BigDecimal> result = new HashMap<>();
            summaries.forEach(
                    (variantId, summary) -> {
                        if (summary != null && summary.availableQty() != null) {
                            result.put(variantId, summary.availableQty());
                        }
                    });
            return result;
        } catch (RuntimeException failure) {
            // Lỗi phụ thuộc tồn kho chỉ làm preview không xác định tồn; lỗi Catalog vẫn báo bình
            // thường.
            log.warn(
                    "Inventory preview unavailable for bundle {} ({})",
                    id,
                    failure.getClass().getSimpleName());
            return Map.of();
        }
    }
}
