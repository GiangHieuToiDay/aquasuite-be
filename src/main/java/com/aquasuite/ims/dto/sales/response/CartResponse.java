package com.aquasuite.ims.dto.sales.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Thông tin tổng hợp giỏ hàng của khách hàng đang đăng nhập (UC-17).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {

    private UUID customerId;
    private List<CartItemResponse> items;
    private int totalItems;
    private BigDecimal totalQuantity;
    private BigDecimal subtotal;
    private boolean hasInvalidItems;
}

