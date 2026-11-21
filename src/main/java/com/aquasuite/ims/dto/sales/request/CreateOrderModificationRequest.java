package com.aquasuite.ims.dto.sales.request;

import jakarta.validation.Valid;
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

    @NotNull
    private String requestType;

    @NotBlank
    @Size(max = 1000)
    private String customerReason;

    @Size(max = 500)
    private String requestedDeliveryAddress;

    @Valid
    private List<CreateOrderModificationItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOrderModificationItemRequest {
        @NotNull
        private UUID orderItemId;

        @NotNull
        private String changeAction;

        private BigDecimal requestedQuantity;

        private UUID requestedVariantId;
    }
}
