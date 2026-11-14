package com.aquasuite.ims.module.fulfillment.internal.repository;

import com.aquasuite.ims.module.fulfillment.internal.entity.DeliveryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DeliveryItemRepository extends JpaRepository<DeliveryItem, UUID> {
    List<DeliveryItem> findByDeliveryIdOrderByDeliveryItemId(UUID deliveryId);
}
