package com.aquasuite.ims.dto.purchasing.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Snapshot một dòng SKU trên đơn mua hàng phục vụ theo dõi tiến độ hàng đang về theo SKU.
 */
@Builder
public record VariantPurchaseOrderLineView(
        UUID purchaseOrderId,
        UUID purchaseOrderItemId,
        String poCode,
        String status,
        String statusLabel,
        UUID supplierId,
        String supplierCode,
        String supplierName,
        String warehouseCode,
        LocalDate orderDate,
        LocalDate expectedDate,
        UUID variantId,
        String unit,
        BigDecimal orderedQty,
        BigDecimal receivedQty,
        BigDecimal remainingQty,
        BigDecimal unitCost
) {}
