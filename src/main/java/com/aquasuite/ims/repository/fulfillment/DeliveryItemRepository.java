package com.aquasuite.ims.repository.fulfillment;

import com.aquasuite.ims.entity.fulfillment.DeliveryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DeliveryItemRepository extends JpaRepository<DeliveryItem, UUID> {
    List<DeliveryItem> findByDeliveryIdOrderByDeliveryItemId(UUID deliveryId);
}
