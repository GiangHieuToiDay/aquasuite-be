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
public class ClosePurchaseOrderRequest {

    @NotBlank(message = "Bắt buộc phải nhập lý do đóng PO")
    @Size(max = 500, message = "Lý do đóng PO không được vượt quá 500 ký tự")
    private String closeReason;
}