package com.aquasuite.ims.module.sales.internal.event;

import java.time.Instant;
import java.util.UUID;

/** Sales-local notification; a durable allocation row is written before the payment commits. */
public record OrderPaymentRecorded(UUID eventId, UUID orderId, Instant occurredAt) {}
