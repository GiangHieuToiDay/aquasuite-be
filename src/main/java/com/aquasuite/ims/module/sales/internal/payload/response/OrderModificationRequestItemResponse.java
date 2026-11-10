package com.aquasuite.ims.module.sales.internal.payload.response;

import com.aquasuite.ims.module.sales.internal.enums.OrderModificationItemAction;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderModificationRequestItemResponse {
    UUID id;
    UUID orderItemId;
    UUID originalVariantId;
    UUID requestedVariantId;
    OrderModificationItemAction changeAction;
    BigDecimal originalQuantity;
    BigDecimal requestedQuantity;
    BigDecimal unitPriceSnapshot;
    OffsetDateTime createdAt;
}
