package com.aquasuite.ims.module.sales.api.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published only after the external e-invoice provider identifier and signing time are persisted.
 * Finance consumes it to settle eligible verified receipts without depending on Sales internals.
 */
public record ElectronicInvoiceLinkedEvent(
        UUID eventId,
        UUID invoiceId,
        UUID orderId,
        UUID customerId,
        UUID linkedBy,
        Instant occurredAt) {

    public ElectronicInvoiceLinkedEvent(UUID invoiceId, UUID orderId, UUID customerId, UUID linkedBy) {
        this(UUID.randomUUID(), invoiceId, orderId, customerId, linkedBy, Instant.now());
    }
}
