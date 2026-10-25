package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.sales.internal.calculator.DiscountPolicyChecker;
import com.aquasuite.ims.module.sales.internal.calculator.DiscountPolicyChecker.DiscountCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscountPolicyCheckerTest {

    private DiscountPolicyChecker checker;

    @BeforeEach
    void setUp() {
        checker = new DiscountPolicyChecker();
    }

    @Test
    @DisplayName("5.2 - Chiết khấu vừa bằng hạn mức nhân viên -> không cần duyệt")
    void testExactAtLimitDoesNotRequireApproval() {
        BigDecimal staffLimit = BigDecimal.valueOf(15); // Hạn mức 15%
        List<BigDecimal> discounts = List.of(
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(15) // Đúng bằng hạn mức
        );

        DiscountCheckResult result = checker.check(discounts, staffLimit);

        assertFalse(result.requiresApproval());
        assertEquals(0, BigDecimal.valueOf(15).compareTo(result.maxDiscountRate()));
        assertEquals(0, staffLimit.compareTo(result.staffDiscountLimit()));
    }

    @Test
    @DisplayName("5.2 - Chỉ cần 1 dòng vượt hạn mức nhân viên -> cần duyệt")
    void testSingleLineExceedingLimitRequiresApproval() {
        BigDecimal staffLimit = BigDecimal.valueOf(10); // Hạn mức 10%
        List<BigDecimal> discounts = List.of(
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(12.5), // Vượt hạn mức 10%
                BigDecimal.valueOf(8)
        );

        DiscountCheckResult result = checker.check(discounts, staffLimit);

        assertTrue(result.requiresApproval());
        assertEquals(0, BigDecimal.valueOf(12.5).compareTo(result.maxDiscountRate()));
    }

    @Test
    @DisplayName("5.2 - Danh sách chiết khấu rỗng hoặc null -> không cần duyệt")
    void testEmptyOrNullList() {
        DiscountCheckResult result = checker.check(List.of(), BigDecimal.valueOf(10));
        assertFalse(result.requiresApproval());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.maxDiscountRate()));
    }
}
