package com.aquasuite.ims.module.sales.internal.controller;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.sales.internal.payload.request.ApproveOrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateOrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.RecordOrderModificationTaxResultRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.ReviewOrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.OrderModificationRequestResponse;
import com.aquasuite.ims.module.sales.internal.service.OrderModificationRequestService;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @GetMapping
    @PreAuthorize("hasAnyRole('SALES_STAFF','ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<Page<OrderModificationRequestResponse>> list(Pageable pageable) {
        return ApiResponse.ok(service.list(pageable, hasRole("ACCOUNTANT") && !hasRole("SHOP_OWNER")));
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("hasAnyRole('SALES_STAFF','ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<OrderModificationRequestResponse> detail(@PathVariable UUID requestId) {
        boolean accountantOnly = hasRole("ACCOUNTANT") && !hasRole("SHOP_OWNER") && !hasRole("SALES_STAFF");
        return ApiResponse.ok(service.detail(requestId, accountantOnly));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ResponseEntity<ApiResponse<OrderModificationRequestResponse>> create(
            @Valid @RequestBody CreateOrderModificationRequest request) {
        OrderModificationRequestResponse response = service.create(request, iamApi.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Order modification request created successfully."));
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ApiResponse<OrderModificationRequestResponse> reject(
            @PathVariable UUID requestId,
            @Valid @RequestBody ReviewOrderModificationRequest request) {
        return ApiResponse.ok(service.reject(requestId, request, iamApi.getCurrentUserId()),
                "Order modification request rejected successfully.");
    }

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ApiResponse<OrderModificationRequestResponse> approve(@PathVariable UUID requestId,
            @Valid @RequestBody ApproveOrderModificationRequest request) {
        return ApiResponse.ok(service.approve(requestId, request, iamApi.getCurrentUserId(), hasRole("SHOP_OWNER")),
                "Order modification request reviewed successfully.");
    }

    @PostMapping("/{requestId}/tax-result")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ApiResponse<OrderModificationRequestResponse> recordTaxResult(@PathVariable UUID requestId,
            @Valid @RequestBody RecordOrderModificationTaxResultRequest request) {
        return ApiResponse.ok(service.recordTaxResult(requestId, request, iamApi.getCurrentUserId()),
                "Tax adjustment or replacement result recorded successfully.");
    }

    @PostMapping("/{requestId}/send-result-email")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ApiResponse<Void> sendResultEmail(@PathVariable UUID requestId) {
        service.sendResultEmail(requestId, iamApi.getCurrentUserId());
        return ApiResponse.ok(null, "Order modification result email accepted for delivery.");
    }

    private boolean hasRole(String role) {
        var current = iamApi.getCurrentUser();
        return current != null && current.roles() != null && current.roles().contains(role);
    }
}
