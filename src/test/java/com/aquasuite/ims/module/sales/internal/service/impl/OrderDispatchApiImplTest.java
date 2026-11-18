package com.aquasuite.ims.module.sales.internal.service.impl;

import com.aquasuite.ims.module.sales.api.OrderDispatchApi;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.enums.OrderModificationRequestStatus;
import com.aquasuite.ims.module.sales.internal.repository.OrderItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderModificationRequestRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDispatchApiImplTest {

    @Mock private OrderRepository orders;
    @Mock private OrderItemRepository items;
    @Mock private OrderModificationRequestRepository modifications;
    @Mock private JdbcTemplate jdbc;
    @InjectMocks private OrderDispatchApiImpl dispatchApi;

    @Test
    void approvedModificationDoesNotBlockDispatch() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().orderId(orderId).orderCode("ORD-001")
                .orderStatus("PROCESSING").totalAmount(new BigDecimal("100.00")).build();

        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        // Only PENDING_REVIEW and PENDING_CANCEL should block; APPROVED should not
        when(modifications.existsByOrderIdAndRequestStatusIn(eq(orderId),
                eq(List.of(OrderModificationRequestStatus.PENDING_REVIEW,
                        OrderModificationRequestStatus.PENDING_CANCEL))))
                .thenReturn(false);
        when(items.findByOrderId(orderId)).thenReturn(List.of());

        OrderDispatchApi.Snapshot snapshot = dispatchApi.read(orderId);
        assertFalse(snapshot.modificationPending());
    }

    @Test
    void pendingReviewBlocksDispatch() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().orderId(orderId).orderCode("ORD-002")
                .orderStatus("PROCESSING").totalAmount(new BigDecimal("200.00")).build();

        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(modifications.existsByOrderIdAndRequestStatusIn(eq(orderId),
                eq(List.of(OrderModificationRequestStatus.PENDING_REVIEW,
                        OrderModificationRequestStatus.PENDING_CANCEL))))
                .thenReturn(true);
        when(items.findByOrderId(orderId)).thenReturn(List.of());

        OrderDispatchApi.Snapshot snapshot = dispatchApi.read(orderId);
        assertTrue(snapshot.modificationPending());
    }

    @Test
    void pendingCancelBlocksDispatch() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().orderId(orderId).orderCode("ORD-003")
                .orderStatus("PROCESSING").totalAmount(new BigDecimal("300.00")).build();

        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(modifications.existsByOrderIdAndRequestStatusIn(eq(orderId),
                eq(List.of(OrderModificationRequestStatus.PENDING_REVIEW,
                        OrderModificationRequestStatus.PENDING_CANCEL))))
                .thenReturn(true);
        when(items.findByOrderId(orderId)).thenReturn(List.of());

        OrderDispatchApi.Snapshot snapshot = dispatchApi.read(orderId);
        assertTrue(snapshot.modificationPending());
    }
}
