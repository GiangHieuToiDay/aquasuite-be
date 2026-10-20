package com.aquasuite.ims.module.inventory.internal.service;

import com.aquasuite.ims.module.inventory.dto.StockSummaryDto;
import com.aquasuite.ims.module.inventory.internal.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface InventoryService {

    StockSummaryDto getInventoryByVariantId(UUID variantId);

    Page<StockMovement> getMovementsByVariantId(UUID variantId, Pageable pageable);

    void manualAdjustStock(UUID variantId, BigDecimal deltaQty, String reason, UUID actorUserId);
}
