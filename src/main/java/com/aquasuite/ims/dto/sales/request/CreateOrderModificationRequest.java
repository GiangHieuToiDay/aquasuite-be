package com.aquasuite.ims.dto.sales.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderModificationRequest {

    @NotNull
    private UUID orderId;

    @NotBlank
    @Size(max = 50)
    private String requestType;

    @NotBlank
    @Size(max = 1000)
    private String customerReason;

    @Size(max = 500)
    private String requestedDeliveryAddress;

    @Size(max = 200)
    private List<@NotNull @Valid CreateOrderModificationItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOrderModificationItemRequest {
        @NotNull
        private UUID orderItemId;

        @NotBlank
        @Size(max = 50)
        private String changeAction;

        @DecimalMin(value = "0.01", message = "Số lượng yêu cầu phải lớn hơn 0")
        @Digits(integer = 10, fraction = 2, message = "Số lượng yêu cầu tối đa 10 chữ số nguyên và 2 chữ số thập phân")
        private BigDecimal requestedQuantity;

        private UUID requestedVariantId;
    }
}
