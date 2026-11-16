package com.aquasuite.ims.module.fulfillment.api;

import com.aquasuite.ims.module.fulfillment.api.dto.OrderDeliverySnapshot;

import java.util.List;
import java.util.UUID;

/** Delivery facts needed by Sales before modifying or cancelling a pre-dispatch order. */
public interface OrderDeliveryQueryApi {
    List<OrderDeliverySnapshot> findByOrderId(UUID orderId);

    /** Joins the caller transaction and retains row locks until it completes. */
    List<OrderDeliverySnapshot> lockByOrderId(UUID orderId);
}
