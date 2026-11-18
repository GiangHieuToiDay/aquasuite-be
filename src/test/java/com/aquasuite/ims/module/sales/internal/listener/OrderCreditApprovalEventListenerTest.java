package com.aquasuite.ims.module.sales.internal.listener;

import com.aquasuite.ims.module.crm.api.event.CustomerCreditProfileRequestEvent;
import com.aquasuite.ims.module.finance.api.CreditControlApi;
import com.aquasuite.ims.module.finance.api.dto.CreditAuthorizationCommand;
import com.aquasuite.ims.module.finance.api.dto.CreditAuthorizationDecision;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.sales.api.event.OrderConfirmedEvent;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.enums.OrderChannel;
import com.aquasuite.ims.module.sales.internal.enums.OrderStatus;
import com.aquasuite.ims.module.sales.internal.enums.OrderType;
import com.aquasuite.ims.module.sales.internal.repository.OrderItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCreditApprovalEventListenerTest {

    private static final UUID REQUEST_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID OWNER_ID = UUID.fromString("20000000-0000-4000-8000-000000000003");
    private static final UUID STAFF_ID = UUID.fromString("20000000-0000-4000-8000-000000000004");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private InventoryApi inventoryApi;

    @Mock
    private CreditControlApi creditControlApi;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderCreditApprovalEventListener listener;

    @Test
    void approvedEvent_whenCreditIsSufficient_transitionsDraftOrderToProcessingAndPublishesOrderConfirmedEvent() {
        UUID orderId = UUID.randomUUID();
        Order draftOrder = Order.builder()
                .orderId(orderId)
                .orderCode("ORD-PRJ-001")
                .customerId(CUSTOMER_ID)
                .salesStaffId(STAFF_ID)
                .orderType(OrderType.PROJECT.name())
                .orderChannel(OrderChannel.DIRECT_SALES.name())
                .orderStatus(OrderStatus.DRAFT.name())
                .paymentStatus("UNPAID")
                .totalAmount(new BigDecimal("100000000.00"))
                .depositAmount(new BigDecimal("20000000.00"))
                .remainingDebtAmount(new BigDecimal("80000000.00"))
                .creditRequestId(REQUEST_ID)
                .deliveryAddress("KĐT Riverside, Bắc Ninh")
                .projectName("Biệt thự Riverside")
                .note("[Chờ Chủ duyệt tăng hạn mức công nợ]")
                .createdAt(OffsetDateTime.now().minusMinutes(5))
                .build();

        when(orderRepository.findByCreditRequestId(REQUEST_ID)).thenReturn(List.of(draftOrder));
        when(creditControlApi.authorize(any(CreditAuthorizationCommand.class)))
                .thenReturn(new CreditAuthorizationDecision(true, "APPROVED", new BigDecimal("100000000.00")));

        listener.onCreditProfileRequestEvent(creditEvent("APPROVED", new BigDecimal("100000000.00")));

        assertThat(draftOrder.getOrderStatus()).isEqualTo(OrderStatus.PROCESSING.name());
        assertThat(draftOrder.getNote()).contains("[Hạn mức công nợ đã được Chủ cửa hàng phê duyệt]");
        verify(orderRepository).save(draftOrder);

        ArgumentCaptor<CreditAuthorizationCommand> cmdCaptor = ArgumentCaptor.forClass(CreditAuthorizationCommand.class);
        verify(creditControlApi).authorize(cmdCaptor.capture());
        assertThat(cmdCaptor.getValue().customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(cmdCaptor.getValue().orderId()).isEqualTo(orderId);
        assertThat(cmdCaptor.getValue().orderChannel()).isEqualTo(OrderChannel.DIRECT_SALES.name());
        assertThat(cmdCaptor.getValue().orderAmount()).isEqualByComparingTo("80000000.00");

        ArgumentCaptor<OrderConfirmedEvent> eventCaptor = ArgumentCaptor.forClass(OrderConfirmedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().orderId()).isEqualTo(orderId);
        assertThat(eventCaptor.getValue().orderCode()).isEqualTo("ORD-PRJ-001");
        assertThat(eventCaptor.getValue().customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(eventCaptor.getValue().projectName()).isEqualTo("Biệt thự Riverside");
    }

    @Test
    void approvedEvent_whenTwoDraftOrdersExceedApprovedAvailableCredit_confirmsFirstAndKeepsSecondInDraftWithNote() {
        UUID order1Id = UUID.randomUUID();
        UUID order2Id = UUID.randomUUID();
        OffsetDateTime baseTime = OffsetDateTime.now();

        Order order1 = Order.builder()
                .orderId(order1Id)
                .orderCode("ORD-PRJ-101")
                .customerId(CUSTOMER_ID)
                .salesStaffId(STAFF_ID)
                .orderType(OrderType.PROJECT.name())
                .orderChannel(OrderChannel.DIRECT_SALES.name())
                .orderStatus(OrderStatus.DRAFT.name())
                .paymentStatus("UNPAID")
                .totalAmount(new BigDecimal("80000000.00"))
                .depositAmount(new BigDecimal("20000000.00"))
                .remainingDebtAmount(new BigDecimal("60000000.00"))
                .creditRequestId(REQUEST_ID)
                .note("[Chờ Chủ duyệt tăng hạn mức công nợ]")
                .createdAt(baseTime.minusMinutes(10))
                .build();

        Order order2 = Order.builder()
                .orderId(order2Id)
                .orderCode("ORD-PRJ-102")
                .customerId(CUSTOMER_ID)
                .salesStaffId(STAFF_ID)
                .orderType(OrderType.PROJECT.name())
                .orderChannel(OrderChannel.DIRECT_SALES.name())
                .orderStatus(OrderStatus.DRAFT.name())
                .paymentStatus("UNPAID")
                .totalAmount(new BigDecimal("70000000.00"))
                .depositAmount(new BigDecimal("20000000.00"))
                .remainingDebtAmount(null) // Exercises totalAmount - depositAmount = 50,000,000 fallback
                .creditRequestId(REQUEST_ID)
                .note("[Chờ Chủ duyệt tăng hạn mức công nợ]")
                .createdAt(baseTime.minusMinutes(2))
                .build();

        when(orderRepository.findByCreditRequestId(REQUEST_ID)).thenReturn(List.of(order1, order2));
        when(creditControlApi.authorize(any(CreditAuthorizationCommand.class))).thenAnswer(invocation -> {
            CreditAuthorizationCommand cmd = invocation.getArgument(0);
            if (order1Id.equals(cmd.orderId())) {
                return new CreditAuthorizationDecision(true, "APPROVED", new BigDecimal("80000000.00"));
            }
            return new CreditAuthorizationDecision(false, "LIMIT_EXCEEDED", new BigDecimal("20000000.00"));
        });

        listener.onCreditProfileRequestEvent(creditEvent("APPROVED", new BigDecimal("80000000.00")));

        assertThat(order1.getOrderStatus()).isEqualTo(OrderStatus.PROCESSING.name());
        assertThat(order1.getNote()).contains("[Hạn mức công nợ đã được Chủ cửa hàng phê duyệt]");

        assertThat(order2.getOrderStatus()).isEqualTo(OrderStatus.DRAFT.name());
        assertThat(order2.getNote()).contains("[Chờ xử lý: Giá trị đơn vượt hạn mức công nợ khả dụng vừa cấp]");

        verify(orderRepository).save(order1);
        verify(orderRepository).save(order2);

        ArgumentCaptor<OrderConfirmedEvent> confirmedCaptor = ArgumentCaptor.forClass(OrderConfirmedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(confirmedCaptor.capture());
        assertThat(confirmedCaptor.getValue().orderId()).isEqualTo(order1Id);
    }

    @Test
    void approvedEvent_whenCustomerIsBlockedOrBlacklisted_keepsDraftWithPendingNoteAndDoesNotPublishConfirmedEvent() {
        UUID orderId = UUID.randomUUID();
        Order draftOrder = Order.builder()
                .orderId(orderId)
                .orderCode("ORD-PRJ-201")
                .customerId(CUSTOMER_ID)
                .orderType(OrderType.PROJECT.name())
                .orderChannel(OrderChannel.DIRECT_SALES.name())
                .orderStatus(OrderStatus.DRAFT.name())
                .paymentStatus("UNPAID")
                .totalAmount(new BigDecimal("40000000.00"))
                .creditRequestId(REQUEST_ID)
                .build();

        when(orderRepository.findByCreditRequestId(REQUEST_ID)).thenReturn(List.of(draftOrder));
        when(creditControlApi.authorize(any(CreditAuthorizationCommand.class)))
                .thenReturn(new CreditAuthorizationDecision(false, "BLACKLISTED", BigDecimal.ZERO));

        listener.onCreditProfileRequestEvent(creditEvent("APPROVED", new BigDecimal("50000000.00")));

        assertThat(draftOrder.getOrderStatus()).isEqualTo(OrderStatus.DRAFT.name());
        assertThat(draftOrder.getNote()).isEqualTo("[Chờ xử lý: Giá trị đơn vượt hạn mức công nợ khả dụng vừa cấp]");
        verify(orderRepository).save(draftOrder);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void rejectedEvent_transitionsDraftOrderToCancelledAndReleasesReservedStock() {
        UUID orderId = UUID.randomUUID();
        UUID variant1Id = UUID.randomUUID();
        UUID variant2Id = UUID.randomUUID();

        Order draftOrder = Order.builder()
                .orderId(orderId)
                .orderCode("ORD-PRJ-301")
                .customerId(CUSTOMER_ID)
                .orderType(OrderType.PROJECT.name())
                .orderChannel(OrderChannel.DIRECT_SALES.name())
                .orderStatus(OrderStatus.DRAFT.name())
                .paymentStatus("UNPAID")
                .totalAmount(new BigDecimal("50000000.00"))
                .creditRequestId(REQUEST_ID)
                .note("[Chờ Chủ duyệt tăng hạn mức công nợ]")
                .build();

        OrderItem item1 = OrderItem.builder()
                .orderItemId(UUID.randomUUID())
                .orderId(orderId)
                .variantId(variant1Id)
                .quantity(new BigDecimal("3.00"))
                .unitPrice(new BigDecimal("10000000.00"))
                .lineTotal(new BigDecimal("30000000.00"))
                .build();

        OrderItem item2 = OrderItem.builder()
                .orderItemId(UUID.randomUUID())
                .orderId(orderId)
                .variantId(variant2Id)
                .quantity(new BigDecimal("2.00"))
                .unitPrice(new BigDecimal("10000000.00"))
                .lineTotal(new BigDecimal("20000000.00"))
                .build();

        when(orderRepository.findByCreditRequestId(REQUEST_ID)).thenReturn(List.of(draftOrder));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item1, item2));

        listener.onCreditProfileRequestEvent(creditEvent("REJECTED", new BigDecimal("50000000.00")));

        assertThat(draftOrder.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(draftOrder.getNote()).contains("[Bị từ chối: Chủ cửa hàng không cấp hạn mức công nợ]");
        verify(orderRepository).save(draftOrder);
        verify(inventoryApi).releaseReservedStock(variant1Id, new BigDecimal("3.00"));
        verify(inventoryApi).releaseReservedStock(variant2Id, new BigDecimal("2.00"));
        verifyNoInteractions(creditControlApi);
        verify(eventPublisher, never()).publishEvent(any());
    }

    private CustomerCreditProfileRequestEvent creditEvent(String action, BigDecimal requestedLimit) {
        return new CustomerCreditProfileRequestEvent(
                UUID.randomUUID(),
                REQUEST_ID,
                CUSTOMER_ID,
                OWNER_ID,
                action,
                requestedLimit,
                30,
                true,
                Instant.now());
    }
}
