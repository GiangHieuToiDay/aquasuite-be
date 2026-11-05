package com.aquasuite.ims.module.sales.internal.service.impl;

import com.aquasuite.ims.module.sales.internal.entity.OrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.enums.OrderModificationRequestStatus;
import com.aquasuite.ims.module.sales.internal.error.OrderModificationErrorCode;
import com.aquasuite.ims.module.sales.internal.mapper.OrderModificationRequestMapper;
import com.aquasuite.ims.module.sales.internal.payload.request.ReviewOrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.OrderModificationRequestResponse;
import com.aquasuite.ims.module.sales.internal.repository.OrderModificationRequestItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderModificationRequestRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.service.OrderModificationRequestService;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderModificationRequestServiceImpl implements OrderModificationRequestService {
    private final OrderModificationRequestRepository requestRepository;
    private final OrderModificationRequestItemRepository itemRepository;
    private final OrderRepository orderRepository;
    private final OrderModificationRequestMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public OrderModificationRequestResponse detail(UUID requestId) {
        return toResponse(findRequest(requestId));
    }

    @Override
    @Transactional
    public OrderModificationRequestResponse reject(
            UUID requestId, ReviewOrderModificationRequest request, UUID actorId) {
        OrderModificationRequest modificationRequest = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new AppException(OrderModificationErrorCode.REQUEST_NOT_FOUND));
        if (modificationRequest.getRequestStatus() != OrderModificationRequestStatus.PENDING_REVIEW) {
            throw new AppException(OrderModificationErrorCode.INVALID_REQUEST_STATE);
        }
        var order = orderRepository.findByIdForUpdate(modificationRequest.getOrderId())
                .orElseThrow(() -> new AppException(OrderModificationErrorCode.ORDER_NOT_ELIGIBLE));
        if (!"PROCESSING".equalsIgnoreCase(order.getOrderStatus())) {
            throw new AppException(OrderModificationErrorCode.ORDER_NOT_ELIGIBLE);
        }

        modificationRequest.setRequestStatus(OrderModificationRequestStatus.REJECTED);
        modificationRequest.setDecisionNote(request.getDecisionNote().trim());
        modificationRequest.setReviewedBy(actorId);
        modificationRequest.setReviewedAt(OffsetDateTime.now());
        return toResponse(modificationRequest);
    }

    private OrderModificationRequest findRequest(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(OrderModificationErrorCode.REQUEST_NOT_FOUND));
    }

    private OrderModificationRequestResponse toResponse(OrderModificationRequest request) {
        OrderModificationRequestResponse response = mapper.toResponse(request);
        response.setItems(mapper.toItemResponses(itemRepository
                .findByModificationRequestIdOrderByCreatedAtAsc(request.getModificationRequestId())));
        return response;
    }
}
