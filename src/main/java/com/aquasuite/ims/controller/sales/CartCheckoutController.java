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
import com.aquasuite.ims.service.sales.impl.CartCheckoutServiceImpl;
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
        return ApiResponse.ok(cartCheckoutService.updateItemQuantity(itemId, request));
    }

    @DeleteMapping("/api/v1/sales/cart/items/{itemId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_STAFF', 'SHOP_OWNER')")
    public ApiResponse<CartResponse> removeItemFromCart(@PathVariable UUID itemId) {
        return ApiResponse.ok(cartCheckoutService.removeItem(itemId), "Đã xóa sản phẩm khỏi giỏ hàng");
    }

    @DeleteMapping("/api/v1/sales/cart")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_STAFF', 'SHOP_OWNER')")
    public ApiResponse<Void> clearCart() {
        cartCheckoutService.clearCart();
        return ApiResponse.ok(null, "Đã xóa toàn bộ giỏ hàng");
    }

    // ── Checkout Endpoints (/api/v1/sales/checkout) ──────────────────────────

    @PostMapping("/api/v1/sales/checkout/preview")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_STAFF', 'SHOP_OWNER')")
    public ApiResponse<OnlineCheckoutPreviewResponse> previewCheckout(
            @jakarta.validation.constraints.Size(max = 100, message = "Tối đa 100 sản phẩm thanh toán cùng lúc")
            @RequestBody(required = false) List<@jakarta.validation.constraints.NotNull(message = "Mã sản phẩm không được rỗng") UUID> selectedItemIds) {
        return ApiResponse.ok(cartCheckoutService.getCheckoutPreview());
    }

    @PostMapping("/api/v1/sales/checkout/place-order")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_STAFF', 'SHOP_OWNER')")
    public ApiResponse<OnlineOrderCheckoutResponse> placeOnlineOrder(
            @Valid @RequestBody PlaceOnlineOrderRequest request) {
        return ApiResponse.ok(cartCheckoutService.placeOnlineOrder(request), "Đặt hàng thành công");
    }
}
