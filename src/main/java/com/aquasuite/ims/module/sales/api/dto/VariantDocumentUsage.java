package com.aquasuite.ims.module.sales.api.dto;

import java.util.UUID;

public record VariantDocumentUsage(UUID variantId, long goodsReceiptCount, long dispatchCount,
                                   long quotationCount, long orderCount, long invoiceCount,
                                   long purchaseOrderCount, long deliveryItemCount, long cartItemCount) {
    public boolean hasBlockingDocument() {
        return goodsReceiptCount + dispatchCount + quotationCount + orderCount + invoiceCount
                + purchaseOrderCount + deliveryItemCount > 0;
    }
}
