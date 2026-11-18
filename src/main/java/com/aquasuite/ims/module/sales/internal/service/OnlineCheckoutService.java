package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.sales.internal.payload.request.PlaceOnlineOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.OnlineCheckoutPreviewResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.OnlineOrderCheckoutResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service nghiệp vụ Đặt hàng và Thanh toán trực tuyến qua PayOS (UC-18).
 */
public interface OnlineCheckoutService {

    OnlineCheckoutPreviewResponse getCheckoutPreview();

    OnlineOrderCheckoutResponse placeOnlineOrder(PlaceOnlineOrderRequest request);

    List<OnlineOrderCheckoutResponse> getMyOnlineOrders();

    OnlineOrderCheckoutResponse getOnlineOrderDetail(UUID orderId);

    OnlineOrderCheckoutResponse verifyAndSyncPayosPayment(UUID orderId);

    OnlineOrderCheckoutResponse createOrRefreshPayosPaymentLink(UUID orderId);
}

