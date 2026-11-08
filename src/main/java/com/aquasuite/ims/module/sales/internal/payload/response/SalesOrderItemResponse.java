package com.aquasuite.ims.module.sales.internal.payload.response;

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
public class SalesOrderItemResponse {

    private UUID orderItemId;
    private UUID orderId;
    private UUID variantId;
    private UUID comboId;
    private String sku;
    private String productName;
    private String variantName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountAmount;
    private BigDecimal lineTotal;
}
