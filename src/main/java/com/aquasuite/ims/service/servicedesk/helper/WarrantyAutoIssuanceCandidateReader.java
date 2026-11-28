package com.aquasuite.ims.service.servicedesk.helper;

import com.aquasuite.ims.entity.fulfillment.DeliveryItem;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Truy vấn thuần đọc nội bộ ServiceDesk tìm các dòng đơn hàng/phiếu giao đủ điều kiện cấp thẻ bảo hành tự động.
 */
@Component
@RequiredArgsConstructor
public class WarrantyAutoIssuanceCandidateReader {

    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<DeliveredItemCandidate> findDeliveredDeliveryCandidates(int limit) {
        List<Object[]> rows = entityManager.createQuery(
                        "SELECT d.deliveryId, d.deliveredAt, d.updatedAt, di " +
                        "FROM Delivery d " +
                        "JOIN DeliveryItem di ON di.deliveryId = d.deliveryId " +
                        "JOIN Order o ON o.orderId = d.orderId " +
                        "WHERE upper(d.deliveryStatus) = 'DELIVERED' " +
                        "AND upper(o.orderStatus) NOT IN ('DRAFT', 'CANCELLED') " +
                        "AND NOT EXISTS (" +
                        "    SELECT 1 FROM WarrantyRecord wr " +
                        "    WHERE wr.deliveryId = d.deliveryId " +
                        "    AND wr.orderItemId = di.orderItemId" +
                        ") " +
                        "ORDER BY COALESCE(d.deliveredAt, d.updatedAt) DESC",
                        Object[].class
                )
                .setMaxResults(Math.max(1, limit))
                .getResultList();

        return rows.stream().map(row -> {
            DeliveryItem item = (DeliveryItem) row[3];
            return new DeliveredItemCandidate(
                    (UUID) row[0],
                    item.getOrderItemId(),
                    item.getVariantId(),
                    (OffsetDateTime) row[1],
                    (OffsetDateTime) row[2],
                    item.getSerialNumbers()
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<CompletedOrderItemCandidate> findCompletedOrderWithoutDeliveryCandidates(int limit) {
        List<Object[]> rows = entityManager.createQuery(
                        "SELECT oi.orderItemId, oi.variantId, o.orderDate, o.updatedAt " +
                        "FROM Order o " +
                        "JOIN OrderItem oi ON oi.orderId = o.orderId " +
                        "WHERE upper(o.orderStatus) = 'COMPLETED' " +
                        "AND NOT EXISTS (" +
                        "    SELECT 1 FROM Delivery d " +
                        "    WHERE d.orderId = o.orderId" +
                        ") " +
                        "AND NOT EXISTS (" +
                        "    SELECT 1 FROM WarrantyRecord wr " +
                        "    WHERE wr.orderItemId = oi.orderItemId" +
                        ") " +
                        "ORDER BY o.updatedAt DESC",
                        Object[].class
                )
                .setMaxResults(Math.max(1, limit))
                .getResultList();

        return rows.stream()
                .map(row -> new CompletedOrderItemCandidate(
                        (UUID) row[0],
                        (UUID) row[1],
                        (OffsetDateTime) row[2],
                        (OffsetDateTime) row[3]
                ))
                .toList();
    }

    public record DeliveredItemCandidate(
            UUID deliveryId,
            UUID orderItemId,
            UUID variantId,
            OffsetDateTime deliveredAt,
            OffsetDateTime updatedAt,
            List<String> serialNumbers
    ) {
    }

    public record CompletedOrderItemCandidate(
            UUID orderItemId,
            UUID variantId,
            OffsetDateTime orderDate,
            OffsetDateTime updatedAt
    ) {
    }
}
