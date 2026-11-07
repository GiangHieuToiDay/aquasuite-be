package com.aquasuite.ims.module.finance.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PendingCustomerRefundCommand(UUID customerId, UUID orderId, UUID invoiceId,
                                           BigDecimal amount, String reason, UUID requestedBy) {}
