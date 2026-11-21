package com.aquasuite.ims.service.sales;

import com.aquasuite.ims.dto.sales.request.AddCartItemRequest;
import com.aquasuite.ims.dto.sales.request.MergeCartItemsRequest;
import com.aquasuite.ims.dto.sales.request.UpdateCartItemQuantityRequest;
import com.aquasuite.ims.dto.sales.response.CartResponse;

import java.util.UUID;

/**
 * Service quản lý giỏ hàng của khách hàng (UC-17).
 */
public interface CartService {

    CartResponse getCurrentCart();

    CartResponse addItem(AddCartItemRequest request);

    CartResponse mergeGuestItems(MergeCartItemsRequest request);

    CartResponse updateItemQuantity(UUID cartItemId, UpdateCartItemQuantityRequest request);

    CartResponse removeItem(UUID cartItemId);

    CartResponse clearCart();
}

