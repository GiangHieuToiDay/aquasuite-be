package com.aquasuite.ims.module.sales.internal.service.impl;

import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.enums.OrderModificationRequestStatus;
import com.aquasuite.ims.module.sales.internal.enums.OrderModificationRequestType;
import com.aquasuite.ims.module.sales.internal.mapper.OrderModificationRequestMapper;
import com.aquasuite.ims.module.sales.internal.payload.request.ReviewOrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.OrderModificationRequestResponse;
import com.aquasuite.ims.module.sales.internal.repository.OrderModificationRequestItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderModificationRequestRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @Mock private OrderModificationRequestMapper mapper;
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
}
