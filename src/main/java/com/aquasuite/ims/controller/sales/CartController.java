package com.aquasuite.ims.controller.sales;

import com.aquasuite.ims.dto.sales.request.AddCartItemRequest;
import com.aquasuite.ims.dto.sales.request.MergeCartItemsRequest;
import com.aquasuite.ims.dto.sales.request.UpdateCartItemQuantityRequest;
import com.aquasuite.ims.dto.sales.response.CartResponse;
import com.aquasuite.ims.service.sales.CartService;
import com.aquasuite.ims.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller quản lý giỏ hàng của khách hàng trực tuyến (UC-17).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sales/cart")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_STAFF', 'SHOP_OWNER')")
    public ApiResponse<CartResponse> getCurrentCart() {
        return ApiResponse.ok(cartService.getCurrentCart());
    }

    @PostMapping("/items")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_STAFF', 'SHOP_OWNER')")
    public ApiResponse<CartResponse> addItem(@Valid @RequestBody AddCartItemRequest request) {
        return ApiResponse.ok(
                cartService.addItem(request),
                "Đã thêm sản phẩm vào giỏ hàng"
        );
    }

    @PostMapping("/items/merge")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_STAFF', 'SHOP_OWNER')")
    public ApiResponse<CartResponse> mergeGuestItems(@Valid @RequestBody MergeCartItemsRequest request) {
        return ApiResponse.ok(
                cartService.mergeGuestItems(request),
                "Đã đồng bộ sản phẩm từ giỏ hàng tạm vào tài khoản"
        );
    }

    @PutMapping("/items/{cartItemId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_STAFF', 'SHOP_OWNER')")
    public ApiResponse<CartResponse> updateItemQuantity(
            @PathVariable UUID cartItemId,
            @Valid @RequestBody UpdateCartItemQuantityRequest request
    ) {
        return ApiResponse.ok(
                cartService.updateItemQuantity(cartItemId, request),
                "Đã cập nhật số lượng sản phẩm"
        );
    }

    @DeleteMapping("/items/{cartItemId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_STAFF', 'SHOP_OWNER')")
    public ApiResponse<CartResponse> removeItem(@PathVariable UUID cartItemId) {
        return ApiResponse.ok(
                cartService.removeItem(cartItemId),
                "Đã xóa sản phẩm khỏi giỏ hàng"
        );
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_STAFF', 'SHOP_OWNER')")
    public ApiResponse<CartResponse> clearCart() {
        return ApiResponse.ok(
                cartService.clearCart(),
                "Đã làm trống giỏ hàng"
        );
    }
}

