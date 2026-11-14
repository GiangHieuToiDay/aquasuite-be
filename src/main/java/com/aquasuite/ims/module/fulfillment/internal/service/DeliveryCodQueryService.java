package com.aquasuite.ims.module.fulfillment.internal.service;

import com.aquasuite.ims.module.fulfillment.api.DeliveryCodQueryApi;
import com.aquasuite.ims.module.fulfillment.api.dto.DeliveryCodSnapshot;
import com.aquasuite.ims.module.fulfillment.internal.entity.Delivery;
import com.aquasuite.ims.module.fulfillment.internal.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryCodQueryService implements DeliveryCodQueryApi {
    private final DeliveryRepository deliveries;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public Optional<DeliveryCodSnapshot> find(UUID id) {
        return deliveries.findById(id).map(this::snapshot);
    }

    /** Caller holds Sales order lock first; joined transaction retains delivery lock. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<DeliveryCodSnapshot> lock(UUID id) {
        return deliveries.lockById(id).map(d -> {
            // A prior find() may have cached the entity before the order lock was acquired.
            entityManager.refresh(d, LockModeType.PESSIMISTIC_WRITE);
            return snapshot(d);
        });
    }

    private DeliveryCodSnapshot snapshot(Delivery d) {
        return new DeliveryCodSnapshot(d.getDeliveryId(), d.getOrderId(), d.getShipperId(),
                d.getDeliveryCode(), d.getDeliveryStatus(), Boolean.TRUE.equals(d.getCodRequired()), d.getCodAmount());
    }
}
