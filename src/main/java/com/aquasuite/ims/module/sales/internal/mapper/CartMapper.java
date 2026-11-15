package com.aquasuite.ims.module.sales.internal.mapper;

import com.aquasuite.ims.module.sales.internal.entity.CartItem;
import com.aquasuite.ims.module.sales.internal.payload.response.CartItemResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.CartResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * MapStruct Mapper chuyển đổi cấu trúc dữ liệu Giỏ hàng (UC-17).
 * Chỉ thực hiện ánh xạ cấu trúc thuần túy, không chứa quyết định nghiệp vụ.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface CartMapper {

    @Mapping(target = "cartItemId", source = "cartItem.cartItemId")
    @Mapping(target = "variantId", source = "cartItem.variantId")
    @Mapping(target = "productId", source = "productId")
    @Mapping(target = "sku", source = "sku")
    @Mapping(target = "productName", source = "productName")
    @Mapping(target = "variantName", source = "variantName")
    @Mapping(target = "unit", source = "unit")
    @Mapping(target = "quantity", source = "cartItem.quantity")
    @Mapping(target = "unitPrice", source = "cartItem.unitPrice")
    @Mapping(target = "currentSellingPrice", source = "currentSellingPrice")
    @Mapping(target = "lineTotal", source = "lineTotal")
    @Mapping(target = "availableQty", source = "availableQty")
    @Mapping(target = "variantStatus", source = "variantStatus")
    @Mapping(target = "inStock", source = "inStock")
    @Mapping(target = "priceChanged", source = "priceChanged")
    @Mapping(target = "updatedAt", source = "cartItem.updatedAt")
    CartItemResponse toItemResponse(
            CartItem cartItem,
            UUID productId,
            String sku,
            String productName,
            String variantName,
            String unit,
            BigDecimal currentSellingPrice,
            BigDecimal lineTotal,
            BigDecimal availableQty,
            String variantStatus,
            boolean inStock,
            boolean priceChanged
    );

    @Mapping(target = "customerId", source = "customerId")
    @Mapping(target = "items", source = "items")
    @Mapping(target = "totalItems", source = "totalItems")
    @Mapping(target = "totalQuantity", source = "totalQuantity")
    @Mapping(target = "subtotal", source = "subtotal")
    @Mapping(target = "hasInvalidItems", source = "hasInvalidItems")
    CartResponse toCartResponse(
            UUID customerId,
            List<CartItemResponse> items,
            int totalItems,
            BigDecimal totalQuantity,
            BigDecimal subtotal,
            boolean hasInvalidItems
    );
}

