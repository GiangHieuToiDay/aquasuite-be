package com.aquasuite.ims.module.sales.internal.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Set;

public final class QuotationConstants {

    private QuotationConstants() {
        // utility class
    }

    /** Danh sách VAT hợp lệ: 0%, 8%, 10% */
    public static final Set<BigDecimal> VALID_VAT_RATES = Set.of(
            BigDecimal.ZERO,
            BigDecimal.valueOf(8),
            BigDecimal.valueOf(10)
    );

    public static final Set<Integer> VALID_VAT_PERCENT_INTEGERS = Collections.unmodifiableSet(
            Set.of(0, 8, 10)
    );

    /** Số ngày hiệu lực mặc định cho báo giá dự án */
    public static final int DEFAULT_VALIDITY_DAYS = 30;

    /** Quy tắc làm tròn tiền: làm tròn 2 chữ số thập phân, HALF_UP */
    public static final int MONEY_SCALE = 2;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /** Quy tắc làm tròn tỷ lệ phần trăm (margin, vat): 2 chữ số thập phân */
    public static final int PERCENT_SCALE = 2;

    public static boolean isValidVatRate(BigDecimal vatRate) {
        if (vatRate == null) {
            return false;
        }
        for (BigDecimal valid : VALID_VAT_RATES) {
            if (vatRate.compareTo(valid) == 0) {
                return true;
            }
        }
        return false;
    }
}
