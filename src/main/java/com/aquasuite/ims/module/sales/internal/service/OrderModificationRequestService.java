package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.sales.internal.payload.request.ReviewOrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.OrderModificationRequestResponse;

import java.util.UUID;

public interface OrderModificationRequestService {
    OrderModificationRequestResponse detail(UUID requestId);

    OrderModificationRequestResponse reject(UUID requestId, ReviewOrderModificationRequest request, UUID actorId);
}
