package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.sales.internal.payload.request.ReviewOrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.OrderModificationRequestResponse;

import java.util.UUID;

public interface OrderModificationRequestService {
    Page<OrderModificationRequestResponse> list(Pageable pageable, boolean accountantOnly);
    OrderModificationRequestResponse detail(UUID requestId);

    OrderModificationRequestResponse reject(UUID requestId, ReviewOrderModificationRequest request, UUID actorId);

    OrderModificationRequestResponse approve(UUID requestId, ApproveOrderModificationRequest request, UUID actorId, boolean shopOwner);

    OrderModificationRequestResponse recordTaxResult(UUID requestId, RecordOrderModificationTaxResultRequest request, UUID actorId);

    void sendResultEmail(UUID requestId, UUID actorId);
}
import com.aquasuite.ims.module.sales.internal.payload.request.ApproveOrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.RecordOrderModificationTaxResultRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
