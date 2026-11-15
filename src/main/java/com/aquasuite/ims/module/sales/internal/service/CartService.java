package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.sales.internal.payload.request.AddCartItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.UpdateCartItemQuantityRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.CartResponse;

import java.util.UUID;

/**
 * Service quản lý giỏ hàng của khách hàng (UC-17).
 */
public interface CartService {

    CartResponse getCurrentCart();

    CartResponse addItem(AddCartItemRequest request);

    CartResponse updateItemQuantity(UUID cartItemId, UpdateCartItemQuantityRequest request);

    CartResponse removeItem(UUID cartItemId);

    CartResponse clearCart();
}

