package com.aquasuite.ims.module.sales.internal.service.impl;

import com.aquasuite.ims.module.finance.api.PaymentSettlementApi;
import com.aquasuite.ims.module.finance.api.dto.VerifiedPaymentSummary;
import com.aquasuite.ims.module.fulfillment.api.OrderDeliveryQueryApi;
import com.aquasuite.ims.module.inventory.api.StockReservationApi;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.entity.OrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.entity.OrderModificationRequestItem;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoice;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoiceItem;
import com.aquasuite.ims.module.sales.internal.enums.OrderModificationItemAction;
import com.aquasuite.ims.module.sales.internal.enums.OrderModificationRequestStatus;
import com.aquasuite.ims.module.sales.internal.enums.OrderModificationRequestType;
import com.aquasuite.ims.module.sales.internal.mapper.OrderModificationRequestMapper;
import com.aquasuite.ims.module.sales.internal.payload.request.ApproveOrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.ReviewOrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.OrderModificationRequestResponse;
import com.aquasuite.ims.module.sales.internal.repository.OrderItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderModificationRequestItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderModificationRequestRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceRepository;
import com.aquasuite.ims.share.exception.AppException;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderModificationRequestServiceImplTest {
    @Mock private OrderModificationRequestRepository requestRepository;
    @Mock private OrderModificationRequestItemRepository itemRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private SalesInvoiceRepository invoiceRepository;
    @Mock private SalesInvoiceItemRepository invoiceItemRepository;
    @Mock private OrderModificationRequestMapper mapper;
    @Mock private OrderDeliveryQueryApi deliveries;
    @Mock private PaymentSettlementApi payments;
    @Mock private StockReservationApi reservations;
    @Mock private ApplicationEventPublisher events;
    @InjectMocks private OrderModificationRequestServiceImpl service;

    @Test
    void rejectLocksProcessingOrderAndRecordsReviewerDecision() {
        UUID requestId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OrderModificationRequest request = OrderModificationRequest.builder()
                .modificationRequestId(requestId).orderId(orderId)
                .requestStatus(OrderModificationRequestStatus.PENDING_REVIEW)
                .requestType(OrderModificationRequestType.FULL_CANCELLATION).build();
        Order order = Order.builder().orderId(orderId).orderStatus("PROCESSING").build();
        OrderModificationRequestResponse response = new OrderModificationRequestResponse();
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(mapper.toResponse(request)).thenReturn(response);
        when(itemRepository.findByModificationRequestIdOrderByCreatedAtAsc(requestId)).thenReturn(java.util.List.of());
        when(mapper.toItemResponses(any())).thenReturn(java.util.List.of());

        service.reject(requestId, ReviewOrderModificationRequest.builder().decisionNote("No stock change allowed").build(), actorId);

        assertEquals(OrderModificationRequestStatus.REJECTED, request.getRequestStatus());
        assertEquals(actorId, request.getReviewedBy());
        assertEquals("No stock change allowed", request.getDecisionNote());
        verify(requestRepository).findByIdForUpdate(requestId);
        verify(orderRepository).findByIdForUpdate(orderId);
    }

    @Test
    void rejectRefusesAlreadyReviewedRequest() {
        UUID requestId = UUID.randomUUID();
        OrderModificationRequest request = OrderModificationRequest.builder()
                .modificationRequestId(requestId)
                .requestStatus(OrderModificationRequestStatus.APPROVED).build();
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        assertThrows(AppException.class, () -> service.reject(requestId,
                ReviewOrderModificationRequest.builder().decisionNote("late").build(), UUID.randomUUID()));
    }

    @Test
    void unpaidDraftReductionUpdatesOrderLineInvoiceAndApprovesAtomically() {
        UUID requestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        OrderModificationRequest request = OrderModificationRequest.builder()
                .modificationRequestId(requestId).orderId(orderId).customerId(customerId)
                .requestStatus(OrderModificationRequestStatus.PENDING_REVIEW)
                .requestType(OrderModificationRequestType.ITEM_CHANGE).build();
        Order order = Order.builder().orderId(orderId).orderStatus("PROCESSING")
                .totalAmount(new BigDecimal("100.00")).build();
        OrderItem orderItem = OrderItem.builder().orderItemId(orderItemId).orderId(orderId).variantId(variantId)
                .quantity(new BigDecimal("2.00")).unitPrice(new BigDecimal("50.00"))
                .discountAmount(BigDecimal.ZERO).lineTotal(new BigDecimal("100.00")).build();
        SalesInvoice invoice = SalesInvoice.builder().invoiceId(invoiceId).status("DRAFT")
                .discountAmount(BigDecimal.ZERO).totalAmount(new BigDecimal("100.00")).build();
        SalesInvoiceItem invoiceItem = SalesInvoiceItem.builder().invoiceItemId(UUID.randomUUID()).invoiceId(invoiceId)
                .orderItemId(orderItemId).variantId(variantId).quantity(new BigDecimal("2.00"))
                .unitPrice(new BigDecimal("50.00")).discountAmount(BigDecimal.ZERO).taxRate(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("100.00")).build();
        OrderModificationRequestItem requested = OrderModificationRequestItem.builder()
                .modificationRequestId(requestId).orderItemId(orderItemId).originalVariantId(variantId)
                .changeAction(OrderModificationItemAction.REDUCE).originalQuantity(new BigDecimal("2.00"))
                .requestedQuantity(BigDecimal.ONE)
                .unitPriceSnapshot(new BigDecimal("50.00")).build();
        ApproveOrderModificationRequest approve = new ApproveOrderModificationRequest();
        approve.setDecisionNote("Reduce unavailable line");

        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(deliveries.lockByOrderId(orderId)).thenReturn(List.of());
        when(invoiceRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(invoice));
        when(payments.getVerifiedPaymentSummary(orderId)).thenReturn(new VerifiedPaymentSummary(orderId, BigDecimal.ZERO));
        when(itemRepository.findByModificationRequestIdOrderByCreatedAtAsc(requestId)).thenReturn(List.of(requested));
        when(orderItemRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(orderItem));
        when(invoiceItemRepository.findByInvoiceIdInForUpdate(List.of(invoiceId))).thenReturn(List.of(invoiceItem));
        when(reservations.findByOrder(orderId)).thenReturn(List.of());
        when(mapper.toResponse(request)).thenReturn(new OrderModificationRequestResponse());
        when(mapper.toItemResponses(any())).thenReturn(List.of());

        service.approve(requestId, approve, actorId, true);

        assertEquals(OrderModificationRequestStatus.APPROVED, request.getRequestStatus());
        assertEquals(BigDecimal.ONE, orderItem.getQuantity());
        assertEquals(new BigDecimal("50.00"), order.getTotalAmount());
        assertEquals(BigDecimal.ONE, invoiceItem.getQuantity());
        verify(events).publishEvent(any());
    }

    @Test
    void paidCancellationWaitsForRefundVerificationInsteadOfCancellingOrder() {
        UUID requestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        OrderModificationRequest request = OrderModificationRequest.builder()
                .modificationRequestId(requestId).orderId(orderId).customerId(UUID.randomUUID())
                .requestStatus(OrderModificationRequestStatus.PENDING_REVIEW)
                .requestType(OrderModificationRequestType.FULL_CANCELLATION).build();
        Order order = Order.builder().orderId(orderId).orderStatus("PROCESSING")
                .totalAmount(new BigDecimal("100.00")).build();
        SalesInvoice invoice = SalesInvoice.builder().invoiceId(UUID.randomUUID()).status("DRAFT").build();
        ApproveOrderModificationRequest approve = new ApproveOrderModificationRequest();
        approve.setDecisionNote("Customer requested cancellation");

        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(deliveries.lockByOrderId(orderId)).thenReturn(List.of());
        when(invoiceRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(invoice));
        when(invoiceRepository.findElectronicallyIssuedStoreInvoicesByOrderId(orderId)).thenReturn(List.of());
        when(payments.getVerifiedPaymentSummary(orderId))
                .thenReturn(new VerifiedPaymentSummary(orderId, new BigDecimal("100.00")));
        when(payments.createPendingCustomerRefund(any())).thenReturn(refundId);
        when(mapper.toResponse(request)).thenReturn(new OrderModificationRequestResponse());
        when(mapper.toItemResponses(any())).thenReturn(List.of());
        when(itemRepository.findByModificationRequestIdOrderByCreatedAtAsc(requestId)).thenReturn(List.of());

        service.approve(requestId, approve, UUID.randomUUID(), true);

        assertEquals(OrderModificationRequestStatus.PENDING_CANCEL, request.getRequestStatus());
        assertEquals(refundId, request.getRefundTransactionId());
        assertEquals("PROCESSING", order.getOrderStatus());
        verify(payments).createPendingCustomerRefund(any());
    }
}
