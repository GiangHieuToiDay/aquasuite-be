package com.aquasuite.ims.module.inventory.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** UC36 stock quantities partitioned by reservation status, without individual serial identity. */
public record SkuReservationStock(UUID variantId, String warehouseCode, BigDecimal onHandQty,
                                  BigDecimal heldQty, BigDecimal availableQty) {
    public List<QuantityStatus> getQuantities() {
        return List.of(new QuantityStatus("HELD", heldQty), new QuantityStatus("AVAILABLE", availableQty));
    }

    public record QuantityStatus(String status, BigDecimal quantity) {}
}
