package com.aquasuite.ims.module.sales.internal.payload.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotationUpsertRequest {

    @NotNull(message = "customerId is required")
    private UUID customerId;

    @NotBlank(message = "projectName is required")
    @Size(max = 255, message = "projectName max length is 255")
    private String projectName;

    private String deliveryAddress;

    private LocalDate validUntil;

    @AllowedVatRate
    private BigDecimal vatRate;

    @PositiveOrZero(message = "shippingFee must be >= 0")
    private BigDecimal shippingFee;

    private String note;

    @Valid
    @Builder.Default
    private List<QuotationItemRequest> items = new ArrayList<>();
}
