package com.aquasuite.ims.module.inventory.api;

import com.aquasuite.ims.module.inventory.api.dto.StockAdjustCommand;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface InventoryApi {
    Optional<StockSummaryDto> getStockSummary(UUID variantId);
    Map<UUID, StockSummaryDto> getStockSummaries(Collection<UUID> variantIds);
    boolean checkAvailability(UUID variantId, BigDecimal requiredQty);
    void reserveStock(UUID variantId, BigDecimal qty);
    void releaseReservedStock(UUID variantId, BigDecimal qty);
    void adjustPhysicalStock(StockAdjustCommand command);
}
