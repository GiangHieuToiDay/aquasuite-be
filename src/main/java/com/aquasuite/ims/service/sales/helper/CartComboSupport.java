package com.aquasuite.ims.service.sales.helper;

import com.aquasuite.ims.dto.catalog.response.CatalogBundleSummaryDto;
import com.aquasuite.ims.dto.inventory.response.StockSummaryDto;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tiện ích dùng chung giữa CartServiceImpl và OnlineCheckoutServiceImpl để kiểm tra tính khả dụng,
 * tồn kho khả dụng và phân bổ giá gói Combo xuống các OrderItem thành phần.
 */
public final class CartComboSupport {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String PUBLISHED_STATUS = "PUBLISHED";

    private CartComboSupport() {}

    public static boolean isComboActiveAndPublished(CatalogBundleSummaryDto bundle, Instant now) {
        if (bundle == null
                || !ACTIVE_STATUS.equalsIgnoreCase(bundle.status())
                || bundle.packagePrice() == null
                || bundle.packagePrice().compareTo(BigDecimal.ZERO) <= 0
                || bundle.items() == null
                || bundle.items().isEmpty()) {
            return false;
        }
        if (bundle.publicationStatus() != null && !PUBLISHED_STATUS.equalsIgnoreCase(bundle.publicationStatus())) {
            return false;
        }
        if (bundle.validFrom() != null && now.isBefore(bundle.validFrom())) {
            return false;
        }
        if (bundle.validUntil() != null && !now.isBefore(bundle.validUntil())) {
            return false;
        }
        return bundle.items().stream()
                .allMatch(item -> item != null
                        && item.variantId() != null
                        && item.quantity() > 0
                        && !Boolean.FALSE.equals(item.sellable()));
    }

    public static BigDecimal computeComboAvailableQty(
            CatalogBundleSummaryDto bundle,
            Map<UUID, StockSummaryDto> stockMap
    ) {
        if (bundle == null || bundle.items() == null || bundle.items().isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal minStock = null;
        for (CatalogBundleSummaryDto.CatalogBundleItemDto item : bundle.items()) {
            if (item == null || item.variantId() == null) {
                return BigDecimal.ZERO;
            }
            StockSummaryDto stock = stockMap.get(item.variantId());
            BigDecimal compAvailable = (stock != null && stock.availableQty() != null)
                    ? stock.availableQty()
                    : BigDecimal.ZERO;
            BigDecimal maxCombos = compAvailable.divide(
                    BigDecimal.valueOf(Math.max(1, item.quantity())),
                    0,
                    RoundingMode.FLOOR);
            if (minStock == null || maxCombos.compareTo(minStock) < 0) {
                minStock = maxCombos;
            }
        }
        return minStock != null ? minStock.max(BigDecimal.ZERO) : BigDecimal.ZERO;
    }

    public static List<String> formatComponents(CatalogBundleSummaryDto bundle) {
        if (bundle == null || bundle.items() == null || bundle.items().isEmpty()) {
            return List.of();
        }
        return bundle.items().stream()
                .map(item -> {
                    String name = item.variantName() != null ? item.variantName() : item.sku();
                    String sku = item.sku() != null ? item.sku() : "";
                    return name + " (" + sku + ") x" + Math.max(1, item.quantity());
                })
                .toList();
    }

    /**
     * Phân bổ giá trị gói Combo xuống từng dòng OrderItem thành phần.
     * Ưu tiên dùng QuotationComboAllocator khi tổng gói <= tổng giá lẻ; giữ tổng chính xác tới 0.01đ.
     */
    public static Map<UUID, BigDecimal> allocateComboNetPrices(
            BigDecimal packageTotal,
            Map<UUID, BigDecimal> componentWeights
    ) {
        if (packageTotal == null || packageTotal.signum() < 0
                || componentWeights == null || componentWeights.isEmpty()) {
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        }
        BigDecimal retailTotal = componentWeights.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (retailTotal.signum() > 0 && packageTotal.compareTo(retailTotal) <= 0) {
            return QuotationComboAllocator.allocate(
                    packageTotal.setScale(2, RoundingMode.HALF_UP),
                    componentWeights);
        }

        // Trường hợp ngoại lệ: tổng gói >= tổng lẻ hiện tại hoặc thành phần chưa có giá lẻ -> phân bổ đều theo tỷ trọng
        BigDecimal net = packageTotal.setScale(2, RoundingMode.HALF_UP);
        Map<UUID, BigDecimal> effectiveWeights = new HashMap<>();
        if (retailTotal.signum() > 0) {
            effectiveWeights.putAll(componentWeights);
        } else {
            componentWeights.keySet().forEach(id -> effectiveWeights.put(id, BigDecimal.ONE));
        }

        BigDecimal effectiveTotal = effectiveWeights.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<UUID, BigDecimal> allocated = new HashMap<>();
        Map<UUID, BigDecimal> remainders = new HashMap<>();
        BigDecimal assigned = BigDecimal.ZERO;

        for (Map.Entry<UUID, BigDecimal> entry : effectiveWeights.entrySet()) {
            BigDecimal numerator = net.multiply(entry.getValue());
            BigDecimal share = numerator.divide(effectiveTotal, 2, RoundingMode.DOWN);
            allocated.put(entry.getKey(), share);
            remainders.put(entry.getKey(), numerator.subtract(share.multiply(effectiveTotal)));
            assigned = assigned.add(share);
        }

        int minorUnits = net.subtract(assigned).movePointRight(2).intValueExact();
        List<UUID> ranked = effectiveWeights.keySet().stream()
                .sorted(Comparator.comparing((UUID id) -> remainders.get(id)).reversed().thenComparing(id -> id))
                .toList();
        for (int i = 0; i < minorUnits; i++) {
            UUID targetId = ranked.get(i);
            allocated.put(targetId, allocated.get(targetId).add(new BigDecimal("0.01")));
        }
        return allocated;
    }
}
