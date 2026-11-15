package com.aquasuite.ims.module.sales.internal.payload.request;

import com.aquasuite.ims.module.sales.internal.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Yêu cầu đặt hàng trực tuyến từ giỏ hàng của khách hàng (UC-18).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceOnlineOrderRequest {

    /** Mã địa chỉ đã lưu trong sổ địa chỉ của khách hàng (tùy chọn). */
    private UUID addressId;

    /** Tên người nhận hàng (dùng khi nhập địa chỉ giao hàng mới hoặc ghi đè). */
    private String recipientName;

    /** Số điện thoại người nhận hàng. */
    private String recipientPhone;

    /** Địa chỉ giao hàng chi tiết (bắt buộc nếu không chọn addressId). */
    private String deliveryAddress;

    /** Lịch giao hàng / khung giờ mong muốn. */
    private String deliverySchedule;

    /** Ghi chú thêm của khách hàng. */
    private String note;

    /** Phương thức thanh toán: BANK_TRANSFER (PayOS) hoặc CASH (COD). */
    @NotNull(message = "Phương thức thanh toán không được để trống")
    private PaymentMethod paymentMethod;
}

