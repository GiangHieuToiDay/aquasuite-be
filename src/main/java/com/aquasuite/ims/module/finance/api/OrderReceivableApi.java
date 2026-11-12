package com.aquasuite.ims.module.finance.api;

import com.aquasuite.ims.module.finance.api.dto.OrderReceivableView;

import java.util.Optional;
import java.util.UUID;

/**
 * Hợp đồng công khai của module Finance cho các module khác đọc tình hình thu tiền của một đơn.
 *
 * <p>Thiết kế cho UC-29 (giữ hàng theo cọc): module gọi dùng {@code depositSatisfied} / {@code verifiedPaid}
 * để quyết định có giữ hàng hay không. Chỉ đọc; không tạo hay thay đổi giao dịch. Không phơi bày
 * {@code finance.internal}; liên kết dữ liệu chéo module bằng scalar UUID.</p>
 */
public interface OrderReceivableApi {

    /**
     * Lấy ảnh chụp thu tiền của một đơn: đã xác minh, đang chờ, còn phải trả và tình trạng cọc.
     *
     * @param orderId mã đơn cần xem
     * @return thông tin thu tiền nếu đơn tồn tại; {@link Optional#empty()} nếu không tìm thấy đơn
     */
    Optional<OrderReceivableView> getOrderReceivable(UUID orderId);
}
