package com.aquasuite.ims.dto.catalog.response;

import java.math.BigDecimal;
import java.util.UUID;

public record VariantDeletionEligibilityResponse(UUID variantId, BigDecimal onHandQty, BigDecimal reservedQty,
                                                 long goodsReceiptCount, long stockMovementCount, long dispatchCount,
                                                 long quotationCount, long orderCount, long invoiceCount,
                                                 long stockCountItemCount, long activeBundleCount,
                                                 long activeBundleOptionCount, long purchaseOrderCount,
                                                 long deliveryItemCount, long cartItemCount,
                                                 boolean eligible) {
}
