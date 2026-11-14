package com.aquasuite.ims.module.inventory.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StockDispatchResult(UUID inventoryId, BigDecimal issuedQty, BigDecimal consumedQty) {}
