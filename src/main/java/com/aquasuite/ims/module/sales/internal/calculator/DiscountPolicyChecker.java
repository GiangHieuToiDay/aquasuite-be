package com.aquasuite.ims.module.sales.internal.calculator;

import java.math.BigDecimal;
import java.util.List;

public class DiscountPolicyChecker {

    public record DiscountCheckResult(
            boolean requiresApproval,
            BigDecimal maxDiscountRate,
            BigDecimal staffDiscountLimit
    ) {}

    public DiscountCheckResult check(List<BigDecimal> discountRates, BigDecimal staffDiscountLimit) {
        BigDecimal limit = (staffDiscountLimit != null) ? staffDiscountLimit : BigDecimal.ZERO;

        if (discountRates == null || discountRates.isEmpty()) {
            return new DiscountCheckResult(false, BigDecimal.ZERO, limit);
        }

        BigDecimal maxRate = BigDecimal.ZERO;
        boolean requiresApproval = false;

        for (BigDecimal rate : discountRates) {
            if (rate != null) {
                if (rate.compareTo(maxRate) > 0) {
                    maxRate = rate;
                }
                if (rate.compareTo(limit) > 0) {
                    requiresApproval = true;
                }
            }
        }

        return new DiscountCheckResult(requiresApproval, maxRate, limit);
    }
}
