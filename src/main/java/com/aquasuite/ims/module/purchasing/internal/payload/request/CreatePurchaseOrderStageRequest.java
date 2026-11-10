package com.aquasuite.ims.module.purchasing.internal.payload.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePurchaseOrderStageRequest {

    private UUID stageId;

    @NotBlank(message = "Tên đợt không được để trống")
    private String stageName;

    private String description;

    @NotNull(message = "Ngày giao hàng dự kiến của đợt không được để trống")
    private LocalDate expectedDeliveryDate;

    @NotNull(message = "Tỷ lệ % của đợt không được để trống")
    @DecimalMin(value = "0.01", message = "Tỷ lệ % phải lớn hơn 0")
    private BigDecimal percent;

    @NotNull(message = "Số tiền của đợt không được để trống")
    @DecimalMin(value = "0.00", message = "Số tiền phải lớn hơn hoặc bằng 0")
    private BigDecimal amount;

    private Integer sortOrder;
}

