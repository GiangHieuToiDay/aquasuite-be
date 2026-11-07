package com.aquasuite.ims.module.purchasing.internal.payload.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelPurchaseOrderRequest {

    @NotBlank(message = "Bắt buộc phải nhập lý do hủy đơn")
    private String cancelReason;
}