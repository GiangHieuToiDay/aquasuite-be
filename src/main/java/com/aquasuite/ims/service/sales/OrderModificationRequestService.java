package com.aquasuite.ims.service.sales;

import com.aquasuite.ims.dto.sales.request.CreateOrderModificationRequest;
import com.aquasuite.ims.dto.sales.request.ReviewOrderModificationRequest;
import com.aquasuite.ims.dto.sales.response.OrderModificationRequestResponse;
import com.aquasuite.ims.dto.sales.request.ApproveOrderModificationRequest;
import com.aquasuite.ims.dto.sales.request.RecordOrderModificationTaxResultRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface OrderModificationRequestService {
    Page<OrderModificationRequestResponse> list(Pageable pageable, boolean accountantOnly);

    OrderModificationRequestResponse detail(UUID requestId, boolean accountantOnly);

    OrderModificationRequestResponse create(CreateOrderModificationRequest body, UUID actorId);

    OrderModificationRequestResponse reject(UUID requestId, ReviewOrderModificationRequest request, UUID actorId);

    OrderModificationRequestResponse approve(UUID requestId, ApproveOrderModificationRequest request, UUID actorId, boolean shopOwner);

    OrderModificationRequestResponse recordTaxResult(UUID requestId, RecordOrderModificationTaxResultRequest request, UUID actorId);

    void sendResultEmail(UUID requestId, UUID actorId);
}
