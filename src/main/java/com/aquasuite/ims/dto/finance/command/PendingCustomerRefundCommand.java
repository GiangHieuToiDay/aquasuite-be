package com.aquasuite.ims.dto.finance.command;

import java.math.BigDecimal;
import java.util.UUID;

public record PendingCustomerRefundCommand(UUID customerId, UUID orderId, UUID invoiceId,
                                           BigDecimal amount, String reason, UUID requestedBy) {}
