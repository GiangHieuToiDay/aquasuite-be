package com.aquasuite.ims.module.inventory.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record VariantInventoryUsage(UUID variantId, BigDecimal onHandQty, BigDecimal reservedQty,
                                    long stockMovementCount, long stockCountItemCount) {
    public boolean canDelete() {
        return onHandQty.signum() == 0 && reservedQty.signum() == 0
                && stockMovementCount == 0 && stockCountItemCount == 0;
    }
}
