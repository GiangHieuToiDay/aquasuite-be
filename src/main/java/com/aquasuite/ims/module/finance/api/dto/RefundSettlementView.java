package com.aquasuite.ims.module.finance.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Status of the pending customer refund created by UC-26 and verified by UC-64. */
public record RefundSettlementView(UUID transactionId, UUID orderId, BigDecimal amount, String status) {}
