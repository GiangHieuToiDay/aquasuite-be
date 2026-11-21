package com.aquasuite.ims.service.fulfillment;

import com.aquasuite.ims.dto.fulfillment.response.DeliveryCodSnapshot;
import com.aquasuite.ims.dto.fulfillment.response.OrderDeliverySnapshot;
import com.aquasuite.ims.entity.fulfillment.Delivery;
import com.aquasuite.ims.repository.fulfillment.DeliveryRepository;
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
public class DeliveryCodQueryService  {
    private final DeliveryRepository deliveries;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Optional<DeliveryCodSnapshot> find(UUID id) {
        return deliveries.findById(id).map(this::snapshot);
    }

    /** Caller holds Sales order lock first; joined transaction retains delivery lock. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<DeliveryCodSnapshot> lock(UUID id) {
        return deliveries.lockById(id).map(d -> {
            // A prior find() may have cached the entity before the order lock was acquired.
            entityManager.refresh(d, LockModeType.PESSIMISTIC_WRITE);
            return snapshot(d);
        });
    }

    @Transactional(readOnly = true)
    public java.util.List<OrderDeliverySnapshot> findByOrderId(UUID orderId) {
        return deliveries.findByOrderIdOrderByCreatedAtAsc(orderId).stream().map(this::orderSnapshot).toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public java.util.List<OrderDeliverySnapshot> lockByOrderId(UUID orderId) {
        return deliveries.lockByOrderId(orderId).stream().map(this::orderSnapshot).toList();
    }

    private DeliveryCodSnapshot snapshot(Delivery d) {
        return new DeliveryCodSnapshot(d.getDeliveryId(), d.getOrderId(), d.getShipperId(),
                d.getDeliveryCode(), d.getDeliveryStatus(), Boolean.TRUE.equals(d.getCodRequired()), d.getCodAmount());
    }

    private OrderDeliverySnapshot orderSnapshot(Delivery d) {
        return new OrderDeliverySnapshot(d.getDeliveryId(), d.getOrderId(), d.getSalesInvoiceId(), d.getDeliveryStatus());
    }
}
