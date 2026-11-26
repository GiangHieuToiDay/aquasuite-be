package com.aquasuite.ims.controller.sales;

import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.dto.sales.request.AddCartItemRequest;
import com.aquasuite.ims.dto.sales.request.MergeCartItemsRequest;
import com.aquasuite.ims.dto.sales.request.PlaceOnlineOrderRequest;
import com.aquasuite.ims.dto.sales.request.UpdateCartItemQuantityRequest;
import com.aquasuite.ims.dto.sales.response.CartResponse;
import com.aquasuite.ims.dto.sales.response.OnlineCheckoutPreviewResponse;
import com.aquasuite.ims.dto.sales.response.OnlineOrderCheckoutResponse;
import com.aquasuite.ims.service.sales.CartCheckoutService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller Mua sắm Storefront: Quản lý giỏ hàng và Đặt hàng thanh toán online PayOS.
 */
@RestController
@org.springframework.validation.annotation.Validated
public class CartCheckoutController {

    private final CartCheckoutService cartCheckoutService;

    @Autowired
    public CartCheckoutController(CartCheckoutService cartCheckoutService) {
        this.cartCheckoutService = cartCheckoutService;
    }

    // ── Cart Endpoints (/api/v1/sales/cart) ───────────────────────────────────

    @GetMapping("/api/v1/sales/cart")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_STAFF', 'SHOP_OWNER')")
    public ApiResponse<CartResponse> getCurrentCart() {
        return ApiResponse.ok(cartCheckoutService.getCurrentCart());
    }

    @PostMapping("/api/v1/sales/cart/items")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_STAFF', 'SHOP_OWNER')")
    public ApiResponse<CartResponse> addItemToCart(@Valid @RequestBody AddCartItemRequest request) {
        return ApiResponse.ok(cartCheckoutService.addItem(request), "Đã thêm sản phẩm vào giỏ hàng");
    }

    @PostMapping("/api/v1/sales/cart/items/merge")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_STAFF', 'SHOP_OWNER')")
    public ApiResponse<CartResponse> mergeGuestCartItems(@Valid @RequestBody MergeCartItemsRequest request) {
        return ApiResponse.ok(cartCheckoutService.mergeGuestItems(request), "Đã đồng bộ sản phẩm từ giỏ hàng tạm vào tài khoản");
    }

    @PutMapping("/api/v1/sales/cart/items/{itemId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_STAFF', 'SHOP_OWNER')")
    public ApiResponse<CartResponse> updateItemQuantity(
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateCartItemQuantityRequest request) {
        return ApiResponse.ok(
                cartCheckoutService.updateItemQuantity(itemId, request),
                "Đã cập nhật số lượng sản phẩm"
        );
    }

    @DeleteMapping("/api/v1/sales/cart/items/{itemId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_STAFF', 'SHOP_OWNER')")
    public ApiResponse<CartResponse> removeItemFromCart(@PathVariable UUID itemId) {
        return ApiResponse.ok(cartCheckoutService.removeItem(itemId), "Đã xóa sản phẩm khỏi giỏ hàng");
    }

    @DeleteMapping("/api/v1/sales/cart")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_STAFF', 'SHOP_OWNER')")
    public ApiResponse<CartResponse> clearCart() {
        return ApiResponse.ok(cartCheckoutService.clearCart(), "Đã làm trống giỏ hàng");
    }

    // ── Online Checkout Endpoints (/api/v1/sales/online-orders) ──────────────

    @GetMapping("/api/v1/sales/online-orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<List<OnlineOrderCheckoutResponse>> getMyOnlineOrders() {
        return ApiResponse.ok(cartCheckoutService.getMyOnlineOrders());
    }

    @GetMapping("/api/v1/sales/online-orders/checkout-preview")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<OnlineCheckoutPreviewResponse> getCheckoutPreview() {
        return ApiResponse.ok(cartCheckoutService.getCheckoutPreview());
    }

    @PostMapping("/api/v1/sales/online-orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<OnlineOrderCheckoutResponse> placeOnlineOrder(
            @Valid @RequestBody PlaceOnlineOrderRequest request) {
        return ApiResponse.ok(cartCheckoutService.placeOnlineOrder(request), "Đặt hàng thành công");
    }

    @GetMapping("/api/v1/sales/online-orders/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<OnlineOrderCheckoutResponse> getOnlineOrderDetail(@PathVariable UUID orderId) {
        return ApiResponse.ok(cartCheckoutService.getOnlineOrderDetail(orderId));
    }

    @PostMapping("/api/v1/sales/online-orders/{orderId}/payos/verify")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<OnlineOrderCheckoutResponse> verifyPayosPayment(@PathVariable UUID orderId) {
        return ApiResponse.ok(cartCheckoutService.verifyAndSyncPayosPayment(orderId));
    }

    @PostMapping("/api/v1/sales/online-orders/{orderId}/payos/payment-link")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<OnlineOrderCheckoutResponse> createOrRefreshPayosPaymentLink(@PathVariable UUID orderId) {
        return ApiResponse.ok(cartCheckoutService.createOrRefreshPayosPaymentLink(orderId));
    }
}
