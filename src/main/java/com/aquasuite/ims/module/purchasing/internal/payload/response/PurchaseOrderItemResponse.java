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
public class PurchaseOrderItemResponse {
    private UUID poItemId;
    private UUID variantId;
    private String sku;
    private String productName;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal receivedQty;
    private BigDecimal remainingQty;
    private BigDecimal unitCost;
    private BigDecimal lineTotal;
    private Integer sortOrder;
    private BigDecimal lastSupplyPrice;
}