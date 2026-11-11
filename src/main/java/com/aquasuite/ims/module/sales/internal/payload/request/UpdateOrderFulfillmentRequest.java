package com.aquasuite.ims.module.sales.internal.payload.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO yêu cầu cập nhật thông tin giao nhận và điều phối đơn hàng (UC-21.2).
 * Chỉ áp dụng khi đơn hàng đang ở trạng thái PROCESSING.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderFulfillmentRequest {

    /** Địa chỉ nhận hàng (tối đa 500 ký tự) */
    @Size(max = 500, message = "Địa chỉ nhận hàng tối đa 500 ký tự")
    private String deliveryAddress;

    /** Lịch hẹn giao cho khách lẻ hoặc tiến độ giao hàng cho công trình */
    @Size(max = 500, message = "Lịch trình giao hàng tối đa 500 ký tự")
    private String deliverySchedule;

    /** Ghi chú điều phối nội bộ */
    @Size(max = 1000, message = "Ghi chú đơn hàng tối đa 1000 ký tự")
    private String note;
}

