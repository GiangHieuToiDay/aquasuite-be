package com.aquasuite.ims.dto.sales.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Thông tin chi tiết một dòng sản phẩm trong giỏ hàng của khách hàng (UC-17).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {

    private UUID cartItemId;
    private UUID variantId;
    private UUID productId;
    private String sku;
    private String productName;
    private String variantName;
    private String imageUrl;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal currentSellingPrice;
    private BigDecimal lineTotal;
    private BigDecimal availableQty;
    private String variantStatus;
    private boolean inStock;
    private boolean priceChanged;
    private OffsetDateTime updatedAt;
}

