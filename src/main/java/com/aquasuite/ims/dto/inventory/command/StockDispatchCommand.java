package com.aquasuite.ims.dto.inventory.command;

import java.math.BigDecimal;
import java.util.UUID;

public record StockDispatchCommand(UUID operationId, UUID deliveryId, UUID orderId, UUID orderItemId,
                                   UUID variantId, String warehouseCode, BigDecimal quantity,
                                   BigDecimal orderedQty, UUID actorId) {}
