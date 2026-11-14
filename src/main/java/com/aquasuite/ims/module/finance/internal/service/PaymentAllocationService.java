package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.internal.payload.request.CreatePaymentAllocationRequest;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationBatchResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationLineResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationTargetsResponse;

import java.util.List;
import java.util.UUID;

/** UC-63 operations; payment recording and verification remain owned by UC-23 and UC-70. */
public interface PaymentAllocationService {
    PaymentAllocationTargetsResponse getTargets(UUID receiptId);

    List<PaymentAllocationLineResponse> getAllocations(UUID receiptId);

    PaymentAllocationBatchResponse allocate(UUID receiptId, CreatePaymentAllocationRequest request, UUID actorId);
}
