package com.aquasuite.ims.module.sales.internal.controller;

import com.aquasuite.ims.module.sales.internal.payload.request.PlaceOnlineOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.OnlineCheckoutPreviewResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.OnlineOrderCheckoutResponse;
import com.aquasuite.ims.module.sales.internal.service.OnlineCheckoutService;
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

/**
 * Controller cho chức năng Đặt hàng và Thanh toán trực tuyến qua PayOS (UC-18).
 */
@RestController
@RequestMapping("/api/v1/sales/online-orders")
@RequiredArgsConstructor
public class OnlineCheckoutController {

    private final OnlineCheckoutService onlineCheckoutService;

    @GetMapping("/checkout-preview")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<OnlineCheckoutPreviewResponse> getCheckoutPreview() {
        return ApiResponse.ok(onlineCheckoutService.getCheckoutPreview());
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<OnlineOrderCheckoutResponse> placeOnlineOrder(
            @Valid @RequestBody PlaceOnlineOrderRequest request
    ) {
        return ApiResponse.ok(onlineCheckoutService.placeOnlineOrder(request));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<OnlineOrderCheckoutResponse> getOnlineOrderDetail(@PathVariable UUID orderId) {
        return ApiResponse.ok(onlineCheckoutService.getOnlineOrderDetail(orderId));
    }

    @PostMapping("/{orderId}/payos/verify")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<OnlineOrderCheckoutResponse> verifyPayosPayment(@PathVariable UUID orderId) {
        return ApiResponse.ok(onlineCheckoutService.verifyAndSyncPayosPayment(orderId));
    }

    @PostMapping("/{orderId}/payos/payment-link")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<OnlineOrderCheckoutResponse> createOrRefreshPayosPaymentLink(@PathVariable UUID orderId) {
        return ApiResponse.ok(onlineCheckoutService.createOrRefreshPayosPaymentLink(orderId));
    }
}

