package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.catalog.internal.exception.BundleErrorCode;
import com.aquasuite.ims.share.exception.AppException;

import org.springframework.stereotype.Service;

import java.math.*;
import java.util.List;

/**
 * Tập trung công thức giá combo bằng BigDecimal, dùng chung cho kiểm dữ liệu ghi và hiển thị.
 * Luồng: giá SKU × số lượng → tổng lẻ → tiền tiết kiệm/phần trăm giảm hoặc priceIssue.
 * Không truy cập database. preview trả trạng thái giá để vẫn xem được combo cần sửa;
 * validate từ chối lưu khi giá không hợp lệ. Kết quả không giữ tồn hoặc cam kết giá giao dịch.
 */
@Service
public class BundlePricingService {
    public static final BigDecimal MAX = new BigDecimal("9999999999999.99");

    /**
     * Dữ liệu giá đầu vào: giá lẻ hiện tại của một SKU và số lượng cần cho một bộ.
     */
    public record Line(BigDecimal price, int quantity) {}

    /**
     * Kết quả tính giá; priceIssue khác null nghĩa là chưa thể hiển thị ưu đãi hợp lệ.
     * Các giá trị không tính được để null, không giả bằng 0.
     */
    public record Price(
            BigDecimal originalTotal,
            BigDecimal savingAmount,
            BigDecimal discountPercent,
            String priceIssue) {}

    /**
     * Tính tổng/tiết kiệm/giảm giá; trả priceIssue thay vì ném lỗi để màn hình quản trị vẫn xem được dữ liệu.
     */
    public Price preview(List<Line> lines, BigDecimal packagePrice) {
        BigDecimal total = BigDecimal.ZERO;
        for (var l : lines) {
            if (l.price() == null || l.price().signum() < 0 || l.quantity() <= 0)
                return new Price(null, null, null, "INVALID_RETAIL_TOTAL");
            total = total.add(l.price().multiply(BigDecimal.valueOf(l.quantity())));
        }
        if (total.compareTo(MAX) > 0 || total.signum() <= 0)
            return new Price(null, null, null, "INVALID_RETAIL_TOTAL");
        if (packagePrice == null || packagePrice.signum() <= 0)
            return new Price(total, null, null, "PACKAGE_PRICE_REQUIRED");
        if (packagePrice.compareTo(total) > 0)
            return new Price(total, null, null, "PACKAGE_EXCEEDS_RETAIL");
        var savings = total.subtract(packagePrice).setScale(2, RoundingMode.HALF_UP);
        return new Price(
                total.setScale(2, RoundingMode.HALF_UP),
                savings,
                savings.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP),
                null);
    }

    /**
     * Dùng trước create/update: kiểm giới hạn tiền và gọi preview; có lỗi giá thì từ chối lưu.
     */
    public void validate(List<Line> lines, BigDecimal packagePrice) {
        if (packagePrice == null
                || packagePrice.signum() <= 0
                || packagePrice.scale() > 2
                || packagePrice.compareTo(MAX) > 0
                || preview(lines, packagePrice).priceIssue() != null)
            throw new AppException(BundleErrorCode.INVALID_DEFINITION);
    }
}
