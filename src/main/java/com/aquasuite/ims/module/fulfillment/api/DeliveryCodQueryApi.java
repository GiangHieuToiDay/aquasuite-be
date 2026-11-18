package com.aquasuite.ims.module.fulfillment.api;

import com.aquasuite.ims.module.fulfillment.api.dto.DeliveryCodSnapshot;
import java.util.Optional;
import java.util.UUID;

/** Scalar delivery facts; the caller must lock the order before locking its delivery. */
public interface DeliveryCodQueryApi {
    Optional<DeliveryCodSnapshot> find(UUID deliveryId);
    Optional<DeliveryCodSnapshot> lock(UUID deliveryId);
}
