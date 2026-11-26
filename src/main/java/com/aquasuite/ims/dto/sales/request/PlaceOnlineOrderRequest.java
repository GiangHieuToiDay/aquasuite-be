package com.aquasuite.ims.dto.sales.request;

import com.aquasuite.ims.enums.sales.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(max = 150, message = "Tên người nhận tối đa 150 ký tự")
    private String recipientName;

    /** Số điện thoại người nhận hàng. */
    @Pattern(regexp = "^\\s*(?:0[35789][0-9]{8})?\\s*$", message = "Số điện thoại người nhận không hợp lệ")
    private String recipientPhone;

    /** Địa chỉ giao hàng chi tiết (bắt buộc nếu không chọn addressId). */
    @Size(max = 500, message = "Địa chỉ giao hàng tối đa 500 ký tự")
    private String deliveryAddress;

    /** Lịch giao hàng / khung giờ mong muốn. */
    @Size(max = 255, message = "Lịch giao hàng tối đa 255 ký tự")
    private String deliverySchedule;

    /** Ghi chú thêm của khách hàng. */
    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String note;

    /** Phương thức thanh toán: BANK_TRANSFER (PayOS) hoặc CASH (COD). */
    @NotNull(message = "Phương thức thanh toán không được để trống")
    private PaymentMethod paymentMethod;
}

