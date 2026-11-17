package com.aquasuite.ims.module.finance.api.dto;

import java.util.UUID;

/**
 * Immutable UC-26 command. A target order/invoice is supplied for a replacement; both are null for a cancellation.
 * A refund transaction is required whenever verified receipt value cannot fit the replacement invoice.
 */
public record OrderModificationSettlementCommand(
        UUID sourceOrderId,
        UUID sourceInvoiceId,
        UUID targetOrderId,
        UUID targetInvoiceId,
        UUID refundTransactionId,
        UUID actorId,
        String reason
) {}
