package com.aquasuite.ims.service.sales;

import com.aquasuite.ims.dto.sales.request.*;
import com.aquasuite.ims.dto.sales.response.*;

import java.util.List;
import java.util.UUID;

/**
 * Service mua sắm storefront hợp nhất (gộp CartService và OnlineCheckoutService).
 */
public interface CartCheckoutService {

    // ── Cart Operations ──────────────────────────────────────────────────────

    CartResponse getCurrentCart();

    CartResponse addItem(AddCartItemRequest request);

    CartResponse mergeGuestItems(MergeCartItemsRequest request);

    CartResponse updateItemQuantity(UUID cartItemId, UpdateCartItemQuantityRequest request);

    CartResponse removeItem(UUID cartItemId);

    CartResponse clearCart();

    // ── Online Checkout Operations ───────────────────────────────────────────

    OnlineCheckoutPreviewResponse getCheckoutPreview();

    OnlineOrderCheckoutResponse placeOnlineOrder(PlaceOnlineOrderRequest request);

    List<OnlineOrderCheckoutResponse> getMyOnlineOrders();

    OnlineOrderCheckoutResponse getOnlineOrderDetail(UUID orderId);

    OnlineOrderCheckoutResponse verifyAndSyncPayosPayment(UUID orderId);

    OnlineOrderCheckoutResponse createOrRefreshPayosPaymentLink(UUID orderId);
}
