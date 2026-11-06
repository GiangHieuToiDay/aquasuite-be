package com.aquasuite.ims.module.purchasing.internal.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

@Component
public class PurchaseOrderCalculator {

    private static final Set<String> CONTINUOUS_UNITS = Set.of(
            "m", "mét", "met", "kg", "kilogram", "g", "gram", "l", "lít", "lit", "m2", "m3"
    );

    public BigDecimal calculateLineTotal(BigDecimal quantity, BigDecimal unitCost) {
        if (quantity == null || unitCost == null) {
            return null;
        }
        return quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isValidQuantity(BigDecimal qty, String unit) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        String normalizedUnit = unit != null ? unit.trim().toLowerCase() : "";
        int scale = qty.stripTrailingZeros().scale();
        if (scale < 0) {
            scale = 0;
        }

        if (CONTINUOUS_UNITS.contains(normalizedUnit)) {
            return scale <= 2;
        }
        return scale == 0;
    }
}