package com.aquasuite.ims.module.sales.internal.controller;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.sales.internal.payload.request.ReviewOrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.OrderModificationRequestResponse;
import com.aquasuite.ims.module.sales.internal.service.OrderModificationRequestService;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sales/order-modification-requests")
public class OrderModificationRequestController {
    private final OrderModificationRequestService service;
    private final IamApi iamApi;

    @GetMapping("/{requestId}")
    @PreAuthorize("hasAnyRole('SALES_STAFF','ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<OrderModificationRequestResponse> detail(@PathVariable UUID requestId) {
        return ApiResponse.ok(service.detail(requestId));
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ApiResponse<OrderModificationRequestResponse> reject(
            @PathVariable UUID requestId,
            @Valid @RequestBody ReviewOrderModificationRequest request) {
        return ApiResponse.ok(service.reject(requestId, request, iamApi.getCurrentUserId()),
                "Order modification request rejected successfully.");
    }
}
