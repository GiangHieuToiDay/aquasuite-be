package com.aquasuite.ims.integration.servicedesk;

import com.aquasuite.ims.repository.fulfillment.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter đọc thông tin phiếu giao hàng thực tế từ Fulfillment cho module ServiceDesk.
 */
@Primary
@Component
@RequiredArgsConstructor
public class FulfillmentWarrantyAdapter implements FulfillmentWarrantyPort {

    private final DeliveryRepository deliveryRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<DeliveryNoteSnapshot> findDeliverySummary(UUID deliveryId) {
        if (deliveryId == null) {
            return Optional.empty();
        }
        return deliveryRepository.findById(deliveryId)
                .map(d -> new DeliveryNoteSnapshot(
                        d.getDeliveryId(),
                        d.getDeliveryCode(),
                        d.getDeliveryStatus(),
                        d.getDeliveredAt(),
                        d.getNote()
                ));
    }
}
