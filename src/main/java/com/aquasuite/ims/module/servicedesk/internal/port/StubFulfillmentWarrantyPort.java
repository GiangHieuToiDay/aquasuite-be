package com.aquasuite.ims.module.servicedesk.internal.port;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Stub mặc định cho {@link FulfillmentWarrantyPort} khi module Fulfillment chưa có API giao hàng.
 */
@Component
public class StubFulfillmentWarrantyPort implements FulfillmentWarrantyPort {

    @Override
    public Optional<DeliveryNoteSnapshot> findDeliverySummary(UUID deliveryId) {
        return Optional.empty();
    }
}

