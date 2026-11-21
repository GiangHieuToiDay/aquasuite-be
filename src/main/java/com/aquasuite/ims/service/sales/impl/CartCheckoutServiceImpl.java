package com.aquasuite.ims.service.sales.impl;

import com.aquasuite.ims.dto.sales.request.*;
import com.aquasuite.ims.dto.sales.response.CartResponse;
import com.aquasuite.ims.dto.sales.response.OnlineCheckoutPreviewResponse;
import com.aquasuite.ims.dto.sales.response.OnlineOrderCheckoutResponse;
import com.aquasuite.ims.service.sales.CartCheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Primary
@RequiredArgsConstructor
public class CartCheckoutServiceImpl implements CartCheckoutService {

    private final CartServiceImpl cartService;
    private final OnlineCheckoutServiceImpl onlineCheckoutService;

    // ── Cart Operations Delegations ──────────────────────────────────────────

    @Override
    public CartResponse getCurrentCart() {
        return cartService != null ? cartService.getCurrentCart() : null;
    }

    @Override
    public CartResponse addItem(AddCartItemRequest request) {
        return cartService != null ? cartService.addItem(request) : null;
    }

    @Override
    public CartResponse mergeGuestItems(MergeCartItemsRequest request) {
        return cartService != null ? cartService.mergeGuestItems(request) : null;
    }

    @Override
    public CartResponse updateItemQuantity(UUID cartItemId, UpdateCartItemQuantityRequest request) {
        return cartService != null ? cartService.updateItemQuantity(cartItemId, request) : null;
    }

    @Override
    public CartResponse removeItem(UUID cartItemId) {
        return cartService != null ? cartService.removeItem(cartItemId) : null;
    }

    @Override
    public CartResponse clearCart() {
        return cartService != null ? cartService.clearCart() : null;
    }

    // ── Online Checkout Delegations ──────────────────────────────────────────

    @Override
    public OnlineCheckoutPreviewResponse getCheckoutPreview() {
        return onlineCheckoutService != null ? onlineCheckoutService.getCheckoutPreview() : null;
    }

    @Override
    public OnlineOrderCheckoutResponse placeOnlineOrder(PlaceOnlineOrderRequest request) {
        return onlineCheckoutService != null ? onlineCheckoutService.placeOnlineOrder(request) : null;
    }

    @Override
    public List<OnlineOrderCheckoutResponse> getMyOnlineOrders() {
        return onlineCheckoutService != null ? onlineCheckoutService.getMyOnlineOrders() : List.of();
    }

    @Override
    public OnlineOrderCheckoutResponse getOnlineOrderDetail(UUID orderId) {
        return onlineCheckoutService != null ? onlineCheckoutService.getOnlineOrderDetail(orderId) : null;
    }

    @Override
    public OnlineOrderCheckoutResponse verifyAndSyncPayosPayment(UUID orderId) {
        return onlineCheckoutService != null ? onlineCheckoutService.verifyAndSyncPayosPayment(orderId) : null;
    }

    @Override
    public OnlineOrderCheckoutResponse createOrRefreshPayosPaymentLink(UUID orderId) {
        return onlineCheckoutService != null ? onlineCheckoutService.createOrRefreshPayosPaymentLink(orderId) : null;
    }
}
