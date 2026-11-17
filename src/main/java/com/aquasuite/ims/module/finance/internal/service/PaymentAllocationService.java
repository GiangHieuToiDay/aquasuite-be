package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.api.dto.OrderModificationSettlementCommand;
import com.aquasuite.ims.module.finance.internal.payload.request.CreatePaymentAllocationRequest;
import com.aquasuite.ims.module.finance.internal.payload.request.ReversePaymentAllocationRequest;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationBatchResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationLineResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationReceiptResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationTargetsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/** UC-63 operations; payment recording and verification remain owned by UC-23 and UC-70. */
public interface PaymentAllocationService {
    Page<PaymentAllocationReceiptResponse> listUnallocatedReceipts(Pageable pageable);

    PaymentAllocationTargetsResponse getTargets(UUID receiptId);

    List<PaymentAllocationLineResponse> getAllocations(UUID receiptId);

    PaymentAllocationBatchResponse allocate(UUID receiptId, CreatePaymentAllocationRequest request, UUID actorId);

    /** Reverses one active allocation with mandatory Accountant evidence; the receipt itself is unchanged. */
    PaymentAllocationLineResponse reverse(UUID receiptId, UUID allocationId, ReversePaymentAllocationRequest request, UUID actorId);

    /** UC-26 calls this after tax replacement/adjustment and any required UC-64 refund verification. */
    void reconcileOrderModification(OrderModificationSettlementCommand command);

    /**
     * Re-evaluates the order's verified receipts using FIFO invoice order.
     * One-stage orders settle all receipts; staged orders settle only their deposit cap.
     */
    void autoAllocateVerifiedReceipt(UUID receiptId, UUID actorId);

    /** Re-runs the same settlement rule when an invoice becomes electronically integrated. */
    void autoAllocateVerifiedReceiptsForOrder(UUID orderId, UUID actorId);
}