package com.aquasuite.ims.service.sales;

import com.aquasuite.ims.dto.sales.request.PlaceOnlineOrderRequest;
import com.aquasuite.ims.dto.sales.response.OnlineCheckoutPreviewResponse;
import com.aquasuite.ims.dto.sales.response.OnlineOrderCheckoutResponse;

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

