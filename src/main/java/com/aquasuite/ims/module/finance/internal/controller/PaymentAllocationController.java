package com.aquasuite.ims.module.finance.internal.controller;

import com.aquasuite.ims.module.finance.internal.payload.request.CreatePaymentAllocationRequest;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationBatchResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationLineResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationTargetsResponse;
import com.aquasuite.ims.module.finance.internal.service.PaymentAllocationService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Accountant-only boundary for UC-63. Transaction creation/verification remain in UC-23 and UC-70. */
@RestController
@RequestMapping("/api/v1/finance/receipts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ACCOUNTANT')")
public class PaymentAllocationController {
    PaymentAllocationService service;
    IamApi iamApi;

    @GetMapping("/{receiptId}/allocation-targets")
    public ApiResponse<PaymentAllocationTargetsResponse> targets(@PathVariable UUID receiptId) {
        return ApiResponse.ok(service.getTargets(receiptId));
    }

    @GetMapping("/{receiptId}/allocations")
    public ApiResponse<List<PaymentAllocationLineResponse>> allocations(@PathVariable UUID receiptId) {
        return ApiResponse.ok(service.getAllocations(receiptId));
    }

    @PostMapping("/{receiptId}/allocations")
    public ApiResponse<PaymentAllocationBatchResponse> allocate(@PathVariable UUID receiptId,
                                                                  @Valid @RequestBody CreatePaymentAllocationRequest request) {
        return ApiResponse.ok(service.allocate(receiptId, request, iamApi.getCurrentUserId()),
                "Payment allocated to electronically issued Store invoice(s) successfully.");
    }
}
