package com.aquasuite.ims.module.purchasing.internal.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockSuggestionResponse {
    private UUID variantId;
    private String sku;
    private String productName;
    private String unit;
    private BigDecimal onHandQty;
    private BigDecimal availableQty;
    private BigDecimal minimumQty;
    private BigDecimal maximumQty;
    private BigDecimal incomingQty;
    private BigDecimal suggestedQty;
    private BigDecimal lastSupplyPrice;
}