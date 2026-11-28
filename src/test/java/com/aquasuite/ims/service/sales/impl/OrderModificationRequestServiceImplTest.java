package com.aquasuite.ims.service.sales.impl;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto;
import com.aquasuite.ims.service.finance.PaymentService;
import com.aquasuite.ims.service.finance.PaymentAllocationService;
import com.aquasuite.ims.dto.finance.response.VerifiedPaymentSummary;
import com.aquasuite.ims.service.fulfillment.DeliveryCodQueryService;
import com.aquasuite.ims.dto.fulfillment.response.OrderDeliverySnapshot;
import com.aquasuite.ims.service.inventory.StockReservationService;
import com.aquasuite.ims.entity.sales.Order;
import com.aquasuite.ims.entity.sales.OrderItem;
import com.aquasuite.ims.entity.sales.OrderModificationRequest;
import com.aquasuite.ims.entity.sales.OrderModificationRequestItem;
import com.aquasuite.ims.entity.sales.SalesInvoice;
import com.aquasuite.ims.entity.sales.SalesInvoiceItem;
import com.aquasuite.ims.enums.sales.OrderModificationItemAction;
import com.aquasuite.ims.enums.sales.OrderModificationRequestStatus;
import com.aquasuite.ims.enums.sales.OrderModificationRequestType;
import com.aquasuite.ims.mapper.sales.OrderModificationRequestMapper;
import com.aquasuite.ims.dto.sales.request.ApproveOrderModificationRequest;
import com.aquasuite.ims.dto.sales.request.CreateOrderModificationRequest;
import com.aquasuite.ims.dto.sales.request.RecordOrderModificationTaxResultRequest;
import com.aquasuite.ims.dto.sales.request.ReviewOrderModificationRequest;
import com.aquasuite.ims.dto.sales.response.OrderModificationRequestResponse;
import com.aquasuite.ims.repository.sales.OrderItemRepository;
import com.aquasuite.ims.repository.sales.OrderModificationRequestItemRepository;
import com.aquasuite.ims.repository.sales.OrderModificationRequestRepository;
import com.aquasuite.ims.repository.sales.OrderRepository;
import com.aquasuite.ims.repository.sales.SalesInvoiceItemRepository;
import com.aquasuite.ims.repository.sales.SalesInvoiceRepository;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
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
    @Mock private DeliveryCodQueryService deliveries;
    @Mock private PaymentService payments;
    @Mock private PaymentAllocationService paymentAllocations;
    @Mock private CatalogLookupService catalogApi;
    @Mock private StockReservationService reservations;
    @Mock private ApplicationEventPublisher events;
    @InjectMocks private OrderModificationRequestServiceImpl service;

    @Test
    void customerRequestRequiresOrderOwnershipAndPersistsAuthenticatedCustomer() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Order order = Order.builder().orderId(orderId).customerId(customerId).orderStatus("PROCESSING").build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(deliveries.lockByOrderId(orderId)).thenReturn(List.of());
        when(requestRepository.existsByOrderIdAndRequestStatusIn(eq(orderId), any())).thenReturn(false);
        when(requestRepository.save(any(OrderModificationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(OrderModificationRequest.class)))
                .thenAnswer(invocation -> {
                    OrderModificationRequest saved = invocation.getArgument(0);
                    return OrderModificationRequestResponse.builder()
                            .id(saved.getModificationRequestId())
                            .orderId(saved.getOrderId())
                            .customerId(saved.getCustomerId())
                            .requestType(saved.getRequestType())
                            .requestStatus(saved.getRequestStatus())
                            .customerReason(saved.getCustomerReason())
                            .createdAt(saved.getCreatedAt())
                            .build();
                });

        var response = service.createForCustomer(CreateOrderModificationRequest.builder()
                        .orderId(orderId)
                        .requestType("ADDRESS_CHANGE")
                        .customerReason("Update delivery location")
                        .requestedDeliveryAddress("New delivery address")
                        .build(),
                userId, customerId);

        assertEquals(orderId, response.getOrderId());
        assertEquals(customerId, response.getCustomerId());
        assertEquals(OrderModificationRequestType.ADDRESS_CHANGE, response.getRequestType());
        assertEquals(OrderModificationRequestStatus.PENDING_REVIEW, response.getRequestStatus());
        verify(requestRepository).save(any(OrderModificationRequest.class));
    }

    @Test
    void customerCannotSubmitModificationRequestForAnotherCustomersOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().orderId(orderId).customerId(UUID.randomUUID())
                .orderStatus("PROCESSING").build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        AppException exception = assertThrows(AppException.class, () -> service.createForCustomer(
                CreateOrderModificationRequest.builder()
                        .orderId(orderId)
                        .requestType("FULL_CANCELLATION")
                        .customerReason("Cancel this order")
                        .build(),
                UUID.randomUUID(), UUID.randomUUID()));

        assertEquals(CommonErrorCode.ACCESS_DENIED, exception.getErrorCode());
        verify(requestRepository, never()).save(any(OrderModificationRequest.class));
        verify(deliveries, never()).lockByOrderId(any());
    }

    // =============================== REJECT ===============================

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
        when(itemRepository.findByModificationRequestIdOrderByCreatedAtAsc(requestId)).thenReturn(List.of());
        when(mapper.toItemResponses(any())).thenReturn(List.of());

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

    // =============================== APPROVE ===============================

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
        assertEquals(0, BigDecimal.ONE.compareTo(orderItem.getQuantity()));
        assertEquals(new BigDecimal("50.00"), order.getTotalAmount());
        assertEquals(0, BigDecimal.ONE.compareTo(invoiceItem.getQuantity()));
        verify(events).publishEvent((Object) any());
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
        when(invoiceRepository.findElectronicallyIntegratedInvoicesByOrderId(orderId)).thenReturn(List.of());
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

    // =============================== ADDRESS_CHANGE on paid order ===============================

    @Test
    void addressChangeOnPaidOrderApprovesImmediatelyAndUpdatesAddress() {
        UUID requestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        OrderModificationRequest request = OrderModificationRequest.builder()
                .modificationRequestId(requestId).orderId(orderId).customerId(UUID.randomUUID())
                .requestStatus(OrderModificationRequestStatus.PENDING_REVIEW)
                .requestType(OrderModificationRequestType.ADDRESS_CHANGE)
                .requestedDeliveryAddress("123 New Street").build();
        Order order = Order.builder().orderId(orderId).orderStatus("PROCESSING")
                .totalAmount(new BigDecimal("500.00")).deliveryAddress("Old Address").build();
        ApproveOrderModificationRequest approve = new ApproveOrderModificationRequest();
        approve.setDecisionNote("Address updated");
        approve.setRequestedDeliveryAddress("123 New Street");

        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(deliveries.lockByOrderId(orderId)).thenReturn(List.of());
        when(invoiceRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of());
        // Order has verified payments — previously would route to PENDING_CANCEL
        when(payments.getVerifiedPaymentSummary(orderId))
                .thenReturn(new VerifiedPaymentSummary(orderId, new BigDecimal("200.00")));
        when(mapper.toResponse(request)).thenReturn(new OrderModificationRequestResponse());
        when(mapper.toItemResponses(any())).thenReturn(List.of());
        when(itemRepository.findByModificationRequestIdOrderByCreatedAtAsc(requestId)).thenReturn(List.of());

        service.approve(requestId, approve, actorId, false);

        assertEquals(OrderModificationRequestStatus.APPROVED, request.getRequestStatus());
        assertEquals(BigDecimal.ZERO, request.getPriceDifference());
        assertEquals("123 New Street", order.getDeliveryAddress());
        // Should not have created a refund
        verify(payments, never()).createPendingCustomerRefund(any());
    }

    // =============================== SWAP ===============================

    @Test
    void swapItemReplacesLineAndReallocatesReservation() {
        UUID requestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        UUID oldVariantId = UUID.randomUUID();
        UUID newVariantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        OrderModificationRequest request = OrderModificationRequest.builder()
                .modificationRequestId(requestId).orderId(orderId).customerId(customerId)
                .requestStatus(OrderModificationRequestStatus.PENDING_REVIEW)
                .requestType(OrderModificationRequestType.ITEM_CHANGE).build();
        Order order = Order.builder().orderId(orderId).orderStatus("PROCESSING")
                .totalAmount(new BigDecimal("100.00")).build();
        OrderItem orderItem = OrderItem.builder().orderItemId(orderItemId).orderId(orderId).variantId(oldVariantId)
                .quantity(new BigDecimal("1.00")).unitPrice(new BigDecimal("100.00"))
                .discountAmount(BigDecimal.ZERO).lineTotal(new BigDecimal("100.00")).productName("Old Product").build();
        OrderItem replacementLine = OrderItem.builder().orderItemId(UUID.randomUUID()).orderId(orderId)
                .variantId(newVariantId).quantity(new BigDecimal("1.00")).unitPrice(new BigDecimal("120.00"))
                .discountAmount(BigDecimal.ZERO).lineTotal(new BigDecimal("120.00")).build();
        SalesInvoice invoice = SalesInvoice.builder().invoiceId(invoiceId).status("DRAFT")
                .discountAmount(BigDecimal.ZERO).totalAmount(new BigDecimal("100.00")).build();
        SalesInvoiceItem invoiceItem = SalesInvoiceItem.builder().invoiceItemId(UUID.randomUUID()).invoiceId(invoiceId)
                .orderItemId(orderItemId).variantId(oldVariantId).quantity(new BigDecimal("1.00"))
                .unitPrice(new BigDecimal("100.00")).discountAmount(BigDecimal.ZERO).taxRate(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("100.00")).build();
        OrderModificationRequestItem requested = OrderModificationRequestItem.builder()
                .modificationRequestId(requestId).orderItemId(orderItemId).originalVariantId(oldVariantId)
                .requestedVariantId(newVariantId).changeAction(OrderModificationItemAction.SWAP)
                .originalQuantity(new BigDecimal("1.00")).unitPriceSnapshot(new BigDecimal("100.00")).build();

        CatalogSkuSummaryDto newSku = CatalogSkuSummaryDto.builder().variantId(newVariantId)
                .sku("NEW-SKU").productName("New Product").variantName("New Variant")
                .sellingPrice(new BigDecimal("120.00")).status("ACTIVE").build();

        ApproveOrderModificationRequest approve = new ApproveOrderModificationRequest();
        approve.setDecisionNote("Swap to upgraded model");

        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(deliveries.lockByOrderId(orderId)).thenReturn(List.of());
        when(invoiceRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(invoice));
        when(payments.getVerifiedPaymentSummary(orderId)).thenReturn(new VerifiedPaymentSummary(orderId, BigDecimal.ZERO));
        when(itemRepository.findByModificationRequestIdOrderByCreatedAtAsc(requestId)).thenReturn(List.of(requested));
        when(orderItemRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(orderItem, replacementLine));
        when(invoiceItemRepository.findByInvoiceIdInForUpdate(List.of(invoiceId))).thenReturn(List.of(invoiceItem));
        when(catalogApi.getSkuSummary(newVariantId)).thenReturn(Optional.of(newSku));
        when(reservations.findByOrder(orderId)).thenReturn(List.of(
                new StockReservationService.Reservation(reservationId, orderId, orderItemId, oldVariantId,
                        "WH-01", new BigDecimal("1.00"), new BigDecimal("1.00"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(replacementLine);
        when(mapper.toResponse(request)).thenReturn(new OrderModificationRequestResponse());
        when(mapper.toItemResponses(any())).thenReturn(List.of());

        service.approve(requestId, approve, actorId, true);

        assertEquals(OrderModificationRequestStatus.APPROVED, request.getRequestStatus());
        assertEquals(BigDecimal.ZERO, orderItem.getQuantity());
        verify(reservations).release(any(StockReservationService.Release.class));
        verify(reservations).reserve(any(StockReservationService.Reserve.class));
    }

    // =============================== FULL_CANCELLATION unpaid ===============================

    @Test
    void fullCancellationUnpaidCancelsOrderAndInvoicesImmediately() {
        UUID requestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        OrderModificationRequest request = OrderModificationRequest.builder()
                .modificationRequestId(requestId).orderId(orderId).customerId(UUID.randomUUID())
                .requestStatus(OrderModificationRequestStatus.PENDING_REVIEW)
                .requestType(OrderModificationRequestType.FULL_CANCELLATION).build();
        Order order = Order.builder().orderId(orderId).orderStatus("PROCESSING")
                .totalAmount(new BigDecimal("100.00")).build();
        SalesInvoice invoice = SalesInvoice.builder().invoiceId(invoiceId).orderId(orderId).status("DRAFT").build();
        ApproveOrderModificationRequest approve = new ApproveOrderModificationRequest();
        approve.setDecisionNote("Cancel order");

        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(deliveries.lockByOrderId(orderId)).thenReturn(List.of());
        when(invoiceRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(invoice));
        when(payments.getVerifiedPaymentSummary(orderId)).thenReturn(new VerifiedPaymentSummary(orderId, BigDecimal.ZERO));
        when(reservations.findByOrder(orderId)).thenReturn(List.of());
        when(mapper.toResponse(request)).thenReturn(new OrderModificationRequestResponse());
        when(mapper.toItemResponses(any())).thenReturn(List.of());
        when(itemRepository.findByModificationRequestIdOrderByCreatedAtAsc(requestId)).thenReturn(List.of());

        service.approve(requestId, approve, actorId, true);

        assertEquals(OrderModificationRequestStatus.APPROVED, request.getRequestStatus());
        assertEquals("CANCELLED", order.getOrderStatus());
        assertEquals("CANCELLED", invoice.getStatus());
    }

    // =============================== recordTaxResult ===============================

    @Test
    void recordTaxResultCancelsOriginalOrderWhenReplacementOrderExists() {
        UUID requestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID replacementOrderId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        OrderModificationRequest request = OrderModificationRequest.builder()
                .modificationRequestId(requestId).orderId(orderId).customerId(UUID.randomUUID())
                .requestStatus(OrderModificationRequestStatus.PENDING_CANCEL)
                .requestType(OrderModificationRequestType.ITEM_CHANGE)
                .replacementOrderId(replacementOrderId).build();
        Order order = Order.builder().orderId(orderId).orderStatus("PROCESSING").build();
        SalesInvoice resultInvoice = SalesInvoice.builder().invoiceId(invoiceId)
                .orderId(replacementOrderId).status("ISSUED").build();
        SalesInvoice draftInvoice = SalesInvoice.builder().invoiceId(UUID.randomUUID())
                .orderId(orderId).status("DRAFT").build();
        RecordOrderModificationTaxResultRequest body = new RecordOrderModificationTaxResultRequest();
        body.setTaxResultReference("TAX-REF-001");
        body.setResultingInvoiceId(invoiceId);
        body.setReplacementOrderId(replacementOrderId);

        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(deliveries.lockByOrderId(orderId)).thenReturn(List.of());
        when(invoiceRepository.findByInvoiceIdForUpdate(invoiceId)).thenReturn(Optional.of(resultInvoice));
        when(invoiceRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(draftInvoice));
        when(reservations.findByOrder(orderId)).thenReturn(List.of());
        when(mapper.toResponse(request)).thenReturn(new OrderModificationRequestResponse());
        when(mapper.toItemResponses(any())).thenReturn(List.of());
        when(itemRepository.findByModificationRequestIdOrderByCreatedAtAsc(requestId)).thenReturn(List.of());

        service.recordTaxResult(requestId, body, actorId);

        assertEquals(OrderModificationRequestStatus.APPROVED, request.getRequestStatus());
        assertEquals("CANCELLED", order.getOrderStatus());
    }

    @Test
    void recordTaxResultFullCancellationCancelsOrder() {
        UUID requestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        OrderModificationRequest request = OrderModificationRequest.builder()
                .modificationRequestId(requestId).orderId(orderId).customerId(UUID.randomUUID())
                .requestStatus(OrderModificationRequestStatus.PENDING_CANCEL)
                .requestType(OrderModificationRequestType.FULL_CANCELLATION).build();
        Order order = Order.builder().orderId(orderId).orderStatus("PROCESSING").build();
        SalesInvoice resultInvoice = SalesInvoice.builder().invoiceId(invoiceId)
                .orderId(orderId).status("ISSUED").build();
        SalesInvoice draftInvoice = SalesInvoice.builder().invoiceId(UUID.randomUUID())
                .orderId(orderId).status("DRAFT").build();
        RecordOrderModificationTaxResultRequest body = new RecordOrderModificationTaxResultRequest();
        body.setTaxResultReference("TAX-REF-002");
        body.setResultingInvoiceId(invoiceId);

        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(deliveries.lockByOrderId(orderId)).thenReturn(List.of());
        when(invoiceRepository.findByInvoiceIdForUpdate(invoiceId)).thenReturn(Optional.of(resultInvoice));
        when(invoiceRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(draftInvoice));
        when(reservations.findByOrder(orderId)).thenReturn(List.of());
        when(mapper.toResponse(request)).thenReturn(new OrderModificationRequestResponse());
        when(mapper.toItemResponses(any())).thenReturn(List.of());
        when(itemRepository.findByModificationRequestIdOrderByCreatedAtAsc(requestId)).thenReturn(List.of());

        service.recordTaxResult(requestId, body, actorId);

        assertEquals(OrderModificationRequestStatus.APPROVED, request.getRequestStatus());
        assertEquals("CANCELLED", order.getOrderStatus());
    }

    // =============================== sendResultEmail ===============================

    @Test
    void sendResultEmailPublishesEventForApprovedRequest() {
        UUID requestId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        OrderModificationRequest request = OrderModificationRequest.builder()
                .modificationRequestId(requestId).orderId(UUID.randomUUID()).customerId(UUID.randomUUID())
                .requestStatus(OrderModificationRequestStatus.APPROVED)
                .requestType(OrderModificationRequestType.FULL_CANCELLATION).build();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        service.sendResultEmail(requestId, actorId);

        verify(events, atLeastOnce()).publishEvent((Object) any());
    }

    @Test
    void sendResultEmailRejectsPendingRequest() {
        UUID requestId = UUID.randomUUID();
        OrderModificationRequest request = OrderModificationRequest.builder()
                .modificationRequestId(requestId)
                .requestStatus(OrderModificationRequestStatus.PENDING_REVIEW)
                .requestType(OrderModificationRequestType.FULL_CANCELLATION).build();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThrows(AppException.class, () -> service.sendResultEmail(requestId, UUID.randomUUID()));
    }

    // =============================== detail ACCOUNTANT access ===============================

    @Test
    void detailBlocksAccountantFromViewingNonPendingCancelRequest() {
        UUID requestId = UUID.randomUUID();
        OrderModificationRequest request = OrderModificationRequest.builder()
                .modificationRequestId(requestId).orderId(UUID.randomUUID())
                .requestStatus(OrderModificationRequestStatus.PENDING_REVIEW)
                .requestType(OrderModificationRequestType.ITEM_CHANGE).build();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        AppException ex = assertThrows(AppException.class, () -> service.detail(requestId, true));
        assertEquals(CommonErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    void detailAllowsAccountantToViewPendingCancelRequest() {
        UUID requestId = UUID.randomUUID();
        OrderModificationRequest request = OrderModificationRequest.builder()
                .modificationRequestId(requestId).orderId(UUID.randomUUID())
                .requestStatus(OrderModificationRequestStatus.PENDING_CANCEL)
                .requestType(OrderModificationRequestType.FULL_CANCELLATION).build();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(mapper.toResponse(request)).thenReturn(OrderModificationRequestResponse.builder().id(requestId).build());
        when(itemRepository.findByModificationRequestIdOrderByCreatedAtAsc(requestId)).thenReturn(List.of());
        when(mapper.toItemResponses(any())).thenReturn(List.of());

        OrderModificationRequestResponse response = service.detail(requestId, true);
        assertEquals(requestId, response.getId());
    }

    // =============================== CREATE ===============================

    @Test
    void createAddressChangeSuccessfully() {
        UUID orderId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order order = Order.builder().orderId(orderId).customerId(customerId).orderStatus("PROCESSING").build();

        CreateOrderModificationRequest body = CreateOrderModificationRequest.builder()
                .orderId(orderId).requestType("ADDRESS_CHANGE")
                .customerReason("Need to change delivery address")
                .requestedDeliveryAddress("456 New Road").build();

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(deliveries.lockByOrderId(orderId)).thenReturn(List.of());
        when(requestRepository.existsByOrderIdAndRequestStatusIn(eq(orderId), any())).thenReturn(false);
        when(requestRepository.save(any(OrderModificationRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(OrderModificationRequest.class)))
                .thenReturn(OrderModificationRequestResponse.builder().id(UUID.randomUUID()).build());
        when(itemRepository.findByModificationRequestIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(mapper.toItemResponses(any())).thenReturn(List.of());

        OrderModificationRequestResponse response = service.create(body, actorId);
        assertNotNull(response);
        verify(requestRepository).save(any(OrderModificationRequest.class));
        verify(events).publishEvent((Object) any());
    }

    @Test
    void createFullCancellationSuccessfully() {
        UUID orderId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order order = Order.builder().orderId(orderId).customerId(customerId).orderStatus("PROCESSING").build();

        CreateOrderModificationRequest body = CreateOrderModificationRequest.builder()
                .orderId(orderId).requestType("FULL_CANCELLATION")
                .customerReason("Customer changed mind").build();

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(deliveries.lockByOrderId(orderId)).thenReturn(List.of());
        when(requestRepository.existsByOrderIdAndRequestStatusIn(eq(orderId), any())).thenReturn(false);
        when(requestRepository.save(any(OrderModificationRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(OrderModificationRequest.class)))
                .thenReturn(OrderModificationRequestResponse.builder().id(UUID.randomUUID()).build());
        when(itemRepository.findByModificationRequestIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(mapper.toItemResponses(any())).thenReturn(List.of());

        OrderModificationRequestResponse response = service.create(body, actorId);
        assertNotNull(response);
        verify(requestRepository).save(any(OrderModificationRequest.class));
    }

    @Test
    void createItemChangeWithReduceSuccessfully() {
        UUID orderId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Order order = Order.builder().orderId(orderId).customerId(customerId).orderStatus("PROCESSING").build();
        OrderItem orderItem = OrderItem.builder().orderItemId(orderItemId).orderId(orderId).variantId(variantId)
                .quantity(new BigDecimal("5.00")).unitPrice(new BigDecimal("20.00")).build();

        CreateOrderModificationRequest body = CreateOrderModificationRequest.builder()
                .orderId(orderId).requestType("ITEM_CHANGE")
                .customerReason("Reduce quantity")
                .items(List.of(CreateOrderModificationRequest.CreateOrderModificationItemRequest.builder()
                        .orderItemId(orderItemId).changeAction("REDUCE")
                        .requestedQuantity(new BigDecimal("2.00")).build()))
                .build();

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(deliveries.lockByOrderId(orderId)).thenReturn(List.of());
        when(requestRepository.existsByOrderIdAndRequestStatusIn(eq(orderId), any())).thenReturn(false);
        when(requestRepository.save(any(OrderModificationRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(orderItem));
        when(itemRepository.save(any(OrderModificationRequestItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(OrderModificationRequest.class)))
                .thenReturn(OrderModificationRequestResponse.builder().id(UUID.randomUUID()).build());
        when(mapper.toItemResponses(any())).thenReturn(List.of());

        OrderModificationRequestResponse response = service.create(body, actorId);
        assertNotNull(response);
    }

    @Test
    void createBlocksDuplicateActiveRequest() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().orderId(orderId).customerId(UUID.randomUUID()).orderStatus("PROCESSING").build();

        CreateOrderModificationRequest body = CreateOrderModificationRequest.builder()
                .orderId(orderId).requestType("ADDRESS_CHANGE")
                .customerReason("Change address")
                .requestedDeliveryAddress("123 Street").build();

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(deliveries.lockByOrderId(orderId)).thenReturn(List.of());
        when(requestRepository.existsByOrderIdAndRequestStatusIn(eq(orderId), any())).thenReturn(true);

        assertThrows(AppException.class, () -> service.create(body, UUID.randomUUID()));
    }

    @Test
    void createBlocksDispatchedOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().orderId(orderId).customerId(UUID.randomUUID()).orderStatus("PROCESSING").build();

        CreateOrderModificationRequest body = CreateOrderModificationRequest.builder()
                .orderId(orderId).requestType("ADDRESS_CHANGE")
                .customerReason("Change address")
                .requestedDeliveryAddress("123 Street").build();

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(deliveries.lockByOrderId(orderId)).thenReturn(List.of(
                new OrderDeliverySnapshot(UUID.randomUUID(), orderId, null, "DISPATCHED")));

        assertThrows(AppException.class, () -> service.create(body, UUID.randomUUID()));
    }

    @Test
    void createSwapValidatesRequestedVariant() {
        UUID orderId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID newVariantId = UUID.randomUUID();
        Order order = Order.builder().orderId(orderId).customerId(customerId).orderStatus("PROCESSING").build();
        OrderItem orderItem = OrderItem.builder().orderItemId(orderItemId).orderId(orderId).variantId(variantId)
                .quantity(new BigDecimal("1.00")).unitPrice(new BigDecimal("100.00")).build();

        CatalogSkuSummaryDto newSku = CatalogSkuSummaryDto.builder().variantId(newVariantId)
                .sku("NEW-SKU").productName("New Product").variantName("New Variant")
                .sellingPrice(new BigDecimal("120.00")).status("ACTIVE").build();

        CreateOrderModificationRequest body = CreateOrderModificationRequest.builder()
                .orderId(orderId).requestType("ITEM_CHANGE")
                .customerReason("Want different model")
                .items(List.of(CreateOrderModificationRequest.CreateOrderModificationItemRequest.builder()
                        .orderItemId(orderItemId).changeAction("SWAP")
                        .requestedVariantId(newVariantId).build()))
                .build();

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(deliveries.lockByOrderId(orderId)).thenReturn(List.of());
        when(requestRepository.existsByOrderIdAndRequestStatusIn(eq(orderId), any())).thenReturn(false);
        when(requestRepository.save(any(OrderModificationRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(orderItem));
        when(catalogApi.getSkuSummary(newVariantId)).thenReturn(Optional.of(newSku));
        when(itemRepository.save(any(OrderModificationRequestItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(OrderModificationRequest.class)))
                .thenReturn(OrderModificationRequestResponse.builder().id(UUID.randomUUID()).build());
        when(mapper.toItemResponses(any())).thenReturn(List.of());

        OrderModificationRequestResponse response = service.create(body, actorId);
        assertNotNull(response);
        verify(catalogApi).getSkuSummary(newVariantId);
    }

    @Test
    void createRemoveItemSuccessfully() {
        UUID orderId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Order order = Order.builder().orderId(orderId).customerId(customerId).orderStatus("PROCESSING").build();
        OrderItem orderItem = OrderItem.builder().orderItemId(orderItemId).orderId(orderId).variantId(variantId)
                .quantity(new BigDecimal("3.00")).unitPrice(new BigDecimal("50.00")).build();

        CreateOrderModificationRequest body = CreateOrderModificationRequest.builder()
                .orderId(orderId).requestType("ITEM_CHANGE")
                .customerReason("Remove this item")
                .items(List.of(CreateOrderModificationRequest.CreateOrderModificationItemRequest.builder()
                        .orderItemId(orderItemId).changeAction("REMOVE").build()))
                .build();

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(deliveries.lockByOrderId(orderId)).thenReturn(List.of());
        when(requestRepository.existsByOrderIdAndRequestStatusIn(eq(orderId), any())).thenReturn(false);
        when(requestRepository.save(any(OrderModificationRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(orderItem));
        when(itemRepository.save(any(OrderModificationRequestItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(OrderModificationRequest.class)))
                .thenReturn(OrderModificationRequestResponse.builder().id(UUID.randomUUID()).build());
        when(mapper.toItemResponses(any())).thenReturn(List.of());

        OrderModificationRequestResponse response = service.create(body, actorId);
        assertNotNull(response);
    }
}
