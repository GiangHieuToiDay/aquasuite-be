package com.aquasuite.ims.module.fulfillment.internal.payload.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DeliveryView {
    private DeliveryView() {}
    public record Summary(UUID deliveryId, String deliveryCode, UUID orderId, Integer batchNo,
        String warehouseCode, UUID shipperId, String deliveryStatus, String recipientName,
        String recipientPhone, String deliveryAddress, OffsetDateTime scheduledDate,
        OffsetDateTime assignedAt, OffsetDateTime startedAt, OffsetDateTime deliveredAt,
        Boolean codRequired, BigDecimal codAmount, String note, OffsetDateTime updatedAt, UUID salesInvoiceId,
        OffsetDateTime confirmedAt, OffsetDateTime dispatchDate) {}
    public record Item(UUID deliveryItemId, UUID orderItemId, UUID variantId, String sku,
        String productName, String variantName, BigDecimal quantity, List<String> serialNumbers) {}
    public record Photo(UUID photoId, String contentUrl) {}
    public record Detail(Summary delivery, UUID assignedBy, String shipperName, String shipperPhone,
        List<Item> items, List<Photo> photos) {}
}
