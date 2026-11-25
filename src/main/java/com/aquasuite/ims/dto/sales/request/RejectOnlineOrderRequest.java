package com.aquasuite.ims.dto.sales.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Yêu cầu từ chối hoặc hủy đơn hàng trực tuyến đang chờ xác nhận (UC-22: PENDING_CONFIRMATION -> CANCELLED).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectOnlineOrderRequest {

    @NotBlank(message = "Lý do từ chối hoặc hủy đơn hàng không được để trống")
    @Size(max = 500, message = "Lý do từ chối hoặc hủy đơn hàng không được vượt quá 500 ký tự")
    private String reason;
}

