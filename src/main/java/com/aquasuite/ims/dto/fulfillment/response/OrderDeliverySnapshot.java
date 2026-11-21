package com.aquasuite.ims.dto.fulfillment.response;

import java.util.UUID;

/** Entity-free delivery state for a Sales order. */
public record OrderDeliverySnapshot(UUID deliveryId, UUID orderId, UUID salesInvoiceId, String status) {}
