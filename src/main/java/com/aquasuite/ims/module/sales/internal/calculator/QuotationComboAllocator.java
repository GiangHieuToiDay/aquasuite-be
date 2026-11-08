package com.aquasuite.ims.module.sales.internal.calculator;

import com.aquasuite.ims.module.sales.internal.entity.QuotationItem;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Allocates the accepted package net to real SKU lines, preserving every currency minor unit. */
public final class QuotationComboAllocator {
    private QuotationComboAllocator() {}

    public static Map<UUID, BigDecimal> allocate(List<QuotationItem> items) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        for (QuotationItem root : items) {
            if (root.getComboId() == null || root.getVariantId() != null) continue;
            var children = items.stream().filter(item -> root.getQuotationItemId().equals(item.getParentItemId()))
                    .sorted(Comparator.comparing(QuotationItem::getQuotationItemId)).toList();
            if (children.isEmpty()) invalid();
            BigDecimal gross = root.getQuantity().multiply(root.getUnitPrice()).setScale(2,RoundingMode.HALF_UP);
            BigDecimal discount = gross.multiply(root.getDiscountRate() == null ? BigDecimal.ZERO : root.getDiscountRate())
                    .divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);
            BigDecimal net = gross.subtract(discount);
            Map<UUID,BigDecimal> weights = new HashMap<>();
            for (var child : children) {
                BigDecimal weight = child.getQuantity().multiply(child.getUnitPrice()).setScale(2,RoundingMode.HALF_UP);
                if (child.getVariantId() == null || weight.signum() < 0) invalid();
                weights.put(child.getQuotationItemId(),weight);
            }
            result.putAll(allocate(net, weights));
        }
        return result;
    }

    /** Shared by quotation snapshots and direct POS combo prices. */
    public static Map<UUID, BigDecimal> allocate(BigDecimal net, Map<UUID, BigDecimal> weights) {
        if (net == null || weights == null || weights.isEmpty()
                || weights.values().stream().anyMatch(w -> w == null || w.signum() < 0)) invalid();
        BigDecimal total = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0 || net.signum() < 0 || net.compareTo(total) > 0) invalid();
        net = net.setScale(2, RoundingMode.UNNECESSARY);
        Map<UUID, BigDecimal> result = new HashMap<>();
        Map<UUID, BigDecimal> remainders = new HashMap<>();
        BigDecimal assigned = BigDecimal.ZERO;
        for (var entry : weights.entrySet()) {
            BigDecimal numerator = net.multiply(entry.getValue());
            BigDecimal share = numerator.divide(total, 2, RoundingMode.DOWN);
            result.put(entry.getKey(), share);
            remainders.put(entry.getKey(), numerator.subtract(share.multiply(total)));
            assigned = assigned.add(share);
        }
        int cents = net.subtract(assigned).movePointRight(2).intValueExact();
        var ranked = weights.keySet().stream().sorted(Comparator
                .comparing((UUID id) -> remainders.get(id)).reversed().thenComparing(id -> id)).toList();
        for (int i = 0; i < cents; i++) {
            UUID id = ranked.get(i);
            result.put(id, result.get(id).add(new BigDecimal("0.01")));
        }
        return result;
    }

    private static void invalid() {
        throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
    }
}
