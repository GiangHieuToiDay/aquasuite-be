package com.aquasuite.ims.module.inventory.api.dto;

import lombok.Builder;
import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record StockAdjustCommand(UUID variantId, StockMovementType movementType, BigDecimal deltaQty,
                                 boolean releaseFromReserved, String referenceType, String referenceId,
                                 String note, UUID actorUserId) {}
