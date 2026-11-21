package com.aquasuite.ims.service.sales.impl;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto;
import com.aquasuite.ims.service.crm.CustomerService;
import com.aquasuite.ims.dto.crm.response.CustomerSummaryDto;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.inventory.InventoryService;
import com.aquasuite.ims.dto.inventory.response.StockSummaryDto;
import com.aquasuite.ims.entity.sales.CartItem;
import com.aquasuite.ims.exception.sales.SalesErrorCode;
import com.aquasuite.ims.mapper.sales.CartMapper;
import com.aquasuite.ims.dto.sales.request.AddCartItemRequest;
import com.aquasuite.ims.dto.sales.request.MergeCartItemsRequest;
import com.aquasuite.ims.dto.sales.request.UpdateCartItemQuantityRequest;
import com.aquasuite.ims.dto.sales.response.CartItemResponse;
import com.aquasuite.ims.dto.sales.response.CartResponse;
import com.aquasuite.ims.repository.sales.CartItemRepository;
import com.aquasuite.ims.service.sales.CartService;
import com.aquasuite.ims.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Triển khai nghiệp vụ quản lý giỏ hàng của khách hàng (UC-17).
 * Giao tiếp liên module thông qua các public contract: IamQueryService, CustomerService, CatalogLookupService, InventoryService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final CartItemRepository cartItemRepository;
    private final CartMapper cartMapper;
    private final IamQueryService iamApi;
    private final CustomerService crmApi;
    private final CatalogLookupService catalogApi;
    private final InventoryService inventoryApi;

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

        ensureSkuActive(skuSummary);

        BigDecimal targetQuantity = saveOrAccumulateCartItem(customerId, skuSummary, request.getQuantity());

        log.info("Updated cart item for customerId={}, variantId={}, quantity={}",
                customerId, variantId, targetQuantity);
        return buildCartResponse(customerId);
    }

    @Override
    @Transactional
    public CartResponse mergeGuestItems(MergeCartItemsRequest request) {
        UUID customerId = resolveCurrentCustomerId();
        Map<UUID, BigDecimal> aggregated = new LinkedHashMap<>();
        for (AddCartItemRequest reqItem : request.getItems()) {
            if (reqItem == null || reqItem.getVariantId() == null || reqItem.getQuantity() == null
                    || reqItem.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            aggregated.merge(reqItem.getVariantId(), reqItem.getQuantity(), BigDecimal::add);
        }

        if (aggregated.isEmpty()) {
            return buildCartResponse(customerId);
        }

        Map<UUID, CatalogSkuSummaryDto> skuMap = catalogApi.getSkuSummaries(aggregated.keySet());
        Map<UUID, StockSummaryDto> stockMap = inventoryApi.getStockSummaries(aggregated.keySet());

        for (Map.Entry<UUID, BigDecimal> entry : aggregated.entrySet()) {
            UUID variantId = entry.getKey();
            CatalogSkuSummaryDto skuSummary = skuMap.get(variantId);
            if (!isSkuAndProductActive(skuSummary)) {
                continue;
            }

            BigDecimal availableQty = Optional.ofNullable(stockMap.get(variantId))
                    .map(StockSummaryDto::availableQty)
                    .orElse(BigDecimal.ZERO);
            if (availableQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Optional<CartItem> existingOpt = cartItemRepository.findByCustomerIdAndVariantId(customerId, variantId);
            BigDecimal currentQty = existingOpt.map(CartItem::getQuantity).orElse(BigDecimal.ZERO);
            BigDecimal desiredTotal = currentQty.add(entry.getValue());
            BigDecimal cappedQty = desiredTotal.min(availableQty);
            if (cappedQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal currentPrice = defaultAmount(skuSummary.sellingPrice());
            CartItem cartItem = existingOpt.orElseGet(() -> CartItem.builder()
                    .customerId(customerId)
                    .variantId(variantId)
                    .quantity(BigDecimal.ZERO)
                    .unitPrice(currentPrice)
                    .build());
            cartItem.setQuantity(cappedQty);
            cartItem.setUnitPrice(currentPrice);
            cartItemRepository.save(cartItem);
        }

        log.info("Merged {} distinct guest cart items for customerId={}", aggregated.size(), customerId);
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

        ensureSkuActive(skuSummary);

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

    private BigDecimal saveOrAccumulateCartItem(
            UUID customerId,
            CatalogSkuSummaryDto skuSummary,
            BigDecimal addQuantity
    ) {
        UUID variantId = skuSummary.variantId();
        BigDecimal currentPrice = defaultAmount(skuSummary.sellingPrice());

        Optional<CartItem> existingOpt = cartItemRepository.findByCustomerIdAndVariantId(customerId, variantId);
        BigDecimal targetQuantity = existingOpt
                .map(existing -> existing.getQuantity().add(addQuantity))
                .orElse(addQuantity);

        validateStockAvailability(variantId, skuSummary.sku(), targetQuantity);

        CartItem cartItem = existingOpt.orElseGet(() -> CartItem.builder()
                .customerId(customerId)
                .variantId(variantId)
                .quantity(BigDecimal.ZERO)
                .unitPrice(currentPrice)
                .build());
        cartItem.setQuantity(targetQuantity);
        cartItem.setUnitPrice(currentPrice);

        try {
            cartItemRepository.save(cartItem);
            return targetQuantity;
        } catch (DataIntegrityViolationException ex) {
            // Xử lý an toàn khi 2 request song song chèn cùng (customer_id, variant_id)
            CartItem concurrentExisting = cartItemRepository.findByCustomerIdAndVariantId(customerId, variantId)
                    .orElseThrow(() -> ex);
            BigDecimal retryTarget = concurrentExisting.getQuantity().add(addQuantity);
            validateStockAvailability(variantId, skuSummary.sku(), retryTarget);
            concurrentExisting.setQuantity(retryTarget);
            concurrentExisting.setUnitPrice(currentPrice);
            cartItemRepository.save(concurrentExisting);
            return retryTarget;
        }
    }

    private void ensureSkuActive(CatalogSkuSummaryDto skuSummary) {
        if (!isSkuAndProductActive(skuSummary)) {
            throw new AppException(SalesErrorCode.SKU_NOT_ACTIVE,
                    skuSummary != null ? skuSummary.sku() : "");
        }
    }

    private boolean isSkuAndProductActive(CatalogSkuSummaryDto skuSummary) {
        if (skuSummary == null || !ACTIVE_STATUS.equalsIgnoreCase(skuSummary.status())) {
            return false;
        }
        return skuSummary.productStatus() == null || ACTIVE_STATUS.equalsIgnoreCase(skuSummary.productStatus());
    }

    private void validateStockAvailability(UUID variantId, String sku, BigDecimal requiredQty) {
        BigDecimal availableQty = inventoryApi.getStockSummary(variantId)
                .map(StockSummaryDto::availableQty)
                .orElse(BigDecimal.ZERO);
        if (availableQty.compareTo(requiredQty) < 0) {
            throw new AppException(
                    SalesErrorCode.INSUFFICIENT_STOCK,
                    sku + " (tồn khả dụng: " + availableQty.stripTrailingZeros().toPlainString() + ")"
            );
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
            String imageUrl = sku != null ? sku.imageUrl() : null;
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

            boolean isVariantActive = isSkuAndProductActive(sku);
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
                    imageUrl,
                    unit,
                    currentSellingPrice,
                    lineTotal,
                    availableQty,
                    isVariantActive ? variantStatus : "INACTIVE",
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

