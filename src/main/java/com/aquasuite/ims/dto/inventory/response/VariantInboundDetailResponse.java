package com.aquasuite.ims.dto.inventory.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Chi tiết tiến độ các đơn mua hàng (PO) và các đợt phiếu nhập kho (GRN) của một SKU.
 */
@Builder
public record VariantInboundDetailResponse(
        UUID variantId,
        BigDecimal onHandQty,
        BigDecimal reservedQty,
        BigDecimal availableQty,
        BigDecimal minimumQty,
        BigDecimal maximumQty,
        BigDecimal incomingQty,
        BigDecimal projectedAvailableQty,
        int openPoCount,
        boolean hasOverduePo,
        List<PurchaseOrderTrackingItem> purchaseOrders
) {

    @Builder
    public record PurchaseOrderTrackingItem(
            UUID purchaseOrderId,
            UUID purchaseOrderItemId,
            String poCode,
            String status,
            String statusLabel,
            UUID supplierId,
            String supplierCode,
            String supplierName,
            LocalDate orderDate,
            LocalDate expectedDate,
            boolean overdue,
            String unit,
            BigDecimal orderedQty,
            BigDecimal receivedQty,
            BigDecimal remainingQty,
            BigDecimal unitCost,
            int progressPercent,
            List<VariantGoodsReceiptBrief> receipts
    ) {}

    @Builder
    public record VariantGoodsReceiptBrief(
            UUID goodsReceiptId,
            String receiptCode,
            LocalDate receiptDate,
            OffsetDateTime confirmedAt,
            BigDecimal receivedQty,
            String confirmedByName
    ) {}
}
