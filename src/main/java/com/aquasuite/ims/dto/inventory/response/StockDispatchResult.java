package com.aquasuite.ims.dto.inventory.response;

import java.math.BigDecimal;
import java.util.UUID;

public record StockDispatchResult(UUID inventoryId, BigDecimal issuedQty, BigDecimal consumedQty) {}
