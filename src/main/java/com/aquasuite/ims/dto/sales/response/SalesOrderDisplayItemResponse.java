package com.aquasuite.ims.dto.sales.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Display rows only. Operational item IDs remain in items/components. */
public record SalesOrderDisplayItemResponse(
        String itemType, UUID comboId, String productName, BigDecimal quantity,
        BigDecimal lineTotal, String sku, BigDecimal unitPrice, BigDecimal discountAmount, SalesOrderItemResponse item,
        List<SalesOrderItemResponse> components) {
}
