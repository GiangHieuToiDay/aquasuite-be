package com.aquasuite.ims.dto.purchasing.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(max = 500, message = "Lý do hủy đơn không được vượt quá 500 ký tự")
    private String cancelReason;
}