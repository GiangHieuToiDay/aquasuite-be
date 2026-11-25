package com.aquasuite.ims.dto.sales.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Yêu cầu xác nhận đơn hàng trực tuyến (UC-22: PENDING_CONFIRMATION -> PROCESSING).
 * Cho phép nhân viên cập nhật thông tin điều phối giao hàng và ghi chú khi chốt đơn với khách.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmOnlineOrderRequest {

    @Size(max = 500, message = "Địa chỉ giao hàng không được vượt quá 500 ký tự")
    private String deliveryAddress;

    @Size(max = 255, message = "Lịch giao hàng dự kiến không được vượt quá 255 ký tự")
    private String deliverySchedule;

    @Size(max = 1000, message = "Ghi chú điều phối không được vượt quá 1000 ký tự")
    private String note;
}

