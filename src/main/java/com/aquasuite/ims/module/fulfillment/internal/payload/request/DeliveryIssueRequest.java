package com.aquasuite.ims.module.fulfillment.internal.payload.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DeliveryIssueRequest(
        @NotNull UUID deliveryId,
        @NotBlank @Pattern(regexp = "WH_MAIN") String warehouseCode,
        @NotBlank @Size(max = 255) String recipientName,
        @NotBlank @Size(max = 32) String recipientPhone,
        @NotBlank @Size(max = 2000) String deliveryAddress,
        OffsetDateTime scheduledDate,
        @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 0) BigDecimal codAmount,
        @Size(max = 1000) String note,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid Line> items) {
    public record Line(@NotNull UUID orderItemId,
                       @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal quantity) {}
}
