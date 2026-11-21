package com.aquasuite.ims.dto.purchasing.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosePurchaseOrderRequest {

    @NotBlank(message = "Bắt buộc phải nhập lý do đóng PO")
    private String closeReason;
}