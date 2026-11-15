package com.aquasuite.ims.module.sales.internal.service.impl;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;
import com.aquasuite.ims.module.sales.internal.entity.CartItem;
import com.aquasuite.ims.module.sales.internal.error.SalesErrorCode;
import com.aquasuite.ims.module.sales.internal.mapper.CartMapper;
import com.aquasuite.ims.module.sales.internal.payload.request.AddCartItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.UpdateCartItemQuantityRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.CartItemResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.CartResponse;
import com.aquasuite.ims.module.sales.internal.repository.CartItemRepository;
import com.aquasuite.ims.module.sales.internal.service.CartService;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Triển khai nghiệp vụ quản lý giỏ hàng của khách hàng (UC-17).
 * Giao tiếp liên module thông qua các public contract: IamApi, CrmApi, CatalogApi, InventoryApi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final CartItemRepository cartItemRepository;
    private final CartMapper cartMapper;
    private final IamApi iamApi;
    private final CrmApi crmApi;
    private final CatalogApi catalogApi;
    private final InventoryApi inventoryApi;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCurrentCart() {
        UUID customerId = resolveCurrentCustomerId();
        return buildCartResponse(customerId);
    }

    @Override
    @Transactional
    public CartResponse addItem(AddCartItemRequest request) {
        validatePositiveQuantity(request.getQuantity());
        UUID customerId = resolveCurrentCustomerId();
        UUID variantId = request.getVariantId();

        CatalogSkuSummaryDto skuSummary = catalogApi.getSkuSummary(variantId)
                .orElseThrow(() -> new AppException(SalesErrorCode.SKU_NOT_FOUND, variantId));

        if (!ACTIVE_STATUS.equalsIgnoreCase(skuSummary.status())) {
            throw new AppException(SalesErrorCode.SKU_NOT_ACTIVE, skuSummary.sku());
        }

        Optional<CartItem> existingOpt = cartItemRepository.findByCustomerIdAndVariantId(customerId, variantId);
        BigDecimal targetQuantity = existingOpt
                .map(existing -> existing.getQuantity().add(request.getQuantity()))
                .orElse(request.getQuantity());

        validateStockAvailability(variantId, skuSummary.sku(), targetQuantity);

        BigDecimal currentPrice = defaultAmount(skuSummary.sellingPrice());

        CartItem cartItem = existingOpt.orElseGet(() -> CartItem.builder()
                .customerId(customerId)
                .variantId(variantId)
                .quantity(BigDecimal.ZERO)
                .unitPrice(currentPrice)
                .build());

        cartItem.setQuantity(targetQuantity);
        cartItem.setUnitPrice(currentPrice);
        cartItemRepository.save(cartItem);

        log.info("Updated cart item for customerId={}, variantId={}, quantity={}",
                customerId, variantId, targetQuantity);
        return buildCartResponse(customerId);
    }

    @Override
    @Transactional
    public CartResponse updateItemQuantity(UUID cartItemId, UpdateCartItemQuantityRequest request) {
        validatePositiveQuantity(request.getQuantity());
        UUID customerId = resolveCurrentCustomerId();

        CartItem cartItem = cartItemRepository.findByCartItemIdAndCustomerId(cartItemId, customerId)
                .orElseThrow(() -> new AppException(SalesErrorCode.CART_ITEM_NOT_FOUND));

        UUID variantId = cartItem.getVariantId();
        CatalogSkuSummaryDto skuSummary = catalogApi.getSkuSummary(variantId)
                .orElseThrow(() -> new AppException(SalesErrorCode.SKU_NOT_FOUND, variantId));

        if (!ACTIVE_STATUS.equalsIgnoreCase(skuSummary.status())) {
            throw new AppException(SalesErrorCode.SKU_NOT_ACTIVE, skuSummary.sku());
        }

        validateStockAvailability(variantId, skuSummary.sku(), request.getQuantity());

        cartItem.setQuantity(request.getQuantity());
        cartItem.setUnitPrice(defaultAmount(skuSummary.sellingPrice()));
        cartItemRepository.save(cartItem);

        log.info("Updated cartItemId={} quantity={} for customerId={}",
                cartItemId, request.getQuantity(), customerId);
        return buildCartResponse(customerId);
    }

    @Override
    @Transactional
    public CartResponse removeItem(UUID cartItemId) {
        UUID customerId = resolveCurrentCustomerId();

        CartItem cartItem = cartItemRepository.findByCartItemIdAndCustomerId(cartItemId, customerId)
                .orElseThrow(() -> new AppException(SalesErrorCode.CART_ITEM_NOT_FOUND));

        cartItemRepository.delete(cartItem);
        log.info("Removed cartItemId={} for customerId={}", cartItemId, customerId);
        return buildCartResponse(customerId);
    }

    @Override
    @Transactional
    public CartResponse clearCart() {
        UUID customerId = resolveCurrentCustomerId();
        cartItemRepository.deleteAllByCustomerId(customerId);
        log.info("Cleared cart for customerId={}", customerId);
        return buildCartResponse(customerId);
    }

    private UUID resolveCurrentCustomerId() {
        UUID userId = iamApi.getCurrentUserId();
        CustomerSummaryDto customer = crmApi.getCustomerByUserId(userId)
                .orElseThrow(() -> new AppException(SalesErrorCode.CUSTOMER_NOT_FOUND));
        return customer.customerId();
    }

    private void validatePositiveQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(SalesErrorCode.INVALID_CART_QUANTITY);
        }
    }

    private void validateStockAvailability(UUID variantId, String sku, BigDecimal requiredQty) {
        BigDecimal availableQty = inventoryApi.getStockSummary(variantId)
                .map(StockSummaryDto::availableQty)
                .orElse(BigDecimal.ZERO);
        if (availableQty.compareTo(requiredQty) < 0) {
            throw new AppException(SalesErrorCode.INSUFFICIENT_STOCK, sku);
        }
    }

    private CartResponse buildCartResponse(UUID customerId) {
        List<CartItem> cartItems = cartItemRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId);
        if (cartItems.isEmpty()) {
            return cartMapper.toCartResponse(
                    customerId,
                    Collections.emptyList(),
                    0,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    false
            );
        }

        Set<UUID> variantIds = cartItems.stream()
                .map(CartItem::getVariantId)
                .collect(Collectors.toSet());

        Map<UUID, CatalogSkuSummaryDto> skuMap = catalogApi.getSkuSummaries(variantIds);
        Map<UUID, StockSummaryDto> stockMap = inventoryApi.getStockSummaries(variantIds);

        List<CartItemResponse> itemResponses = new ArrayList<>(cartItems.size());
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean hasInvalidItems = false;

        for (CartItem item : cartItems) {
            CatalogSkuSummaryDto sku = skuMap.get(item.getVariantId());
            StockSummaryDto stock = stockMap.get(item.getVariantId());

            UUID productId = sku != null ? sku.productId() : null;
            String skuCode = sku != null ? sku.sku() : "N/A";
            String productName = sku != null ? sku.productName() : "Sản phẩm không còn tồn tại";
            String variantName = sku != null ? sku.variantName() : "";
            String unit = sku != null ? sku.unit() : "";
            String variantStatus = sku != null && sku.status() != null ? sku.status() : "INACTIVE";

            BigDecimal storedUnitPrice = defaultAmount(item.getUnitPrice());
            BigDecimal currentSellingPrice = sku != null && sku.sellingPrice() != null
                    ? sku.sellingPrice()
                    : storedUnitPrice;
            BigDecimal quantity = defaultAmount(item.getQuantity());
            BigDecimal lineTotal = currentSellingPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);

            BigDecimal availableQty = stock != null && stock.availableQty() != null
                    ? stock.availableQty()
                    : BigDecimal.ZERO;

            boolean isVariantActive = ACTIVE_STATUS.equalsIgnoreCase(variantStatus);
            boolean inStock = isVariantActive && availableQty.compareTo(quantity) >= 0;
            boolean priceChanged = currentSellingPrice.compareTo(storedUnitPrice) != 0;

            if (!inStock || !isVariantActive) {
                hasInvalidItems = true;
            }

            totalQuantity = totalQuantity.add(quantity);
            subtotal = subtotal.add(lineTotal);

            itemResponses.add(cartMapper.toItemResponse(
                    item,
                    productId,
                    skuCode,
                    productName,
                    variantName,
                    unit,
                    currentSellingPrice,
                    lineTotal,
                    availableQty,
                    variantStatus,
                    inStock,
                    priceChanged
            ));
        }

        return cartMapper.toCartResponse(
                customerId,
                itemResponses,
                itemResponses.size(),
                totalQuantity,
                subtotal,
                hasInvalidItems
        );
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}

