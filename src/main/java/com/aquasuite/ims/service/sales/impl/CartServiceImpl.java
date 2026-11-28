package com.aquasuite.ims.service.sales.impl;

import com.aquasuite.ims.dto.catalog.response.CatalogBundleSummaryDto;
import com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto;
import com.aquasuite.ims.dto.crm.response.CustomerSummaryDto;
import com.aquasuite.ims.dto.inventory.response.StockSummaryDto;
import com.aquasuite.ims.dto.sales.request.AddCartItemRequest;
import com.aquasuite.ims.dto.sales.request.MergeCartItemsRequest;
import com.aquasuite.ims.dto.sales.request.UpdateCartItemQuantityRequest;
import com.aquasuite.ims.dto.sales.response.CartItemResponse;
import com.aquasuite.ims.dto.sales.response.CartResponse;
import com.aquasuite.ims.entity.sales.CartItem;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.sales.SalesErrorCode;
import com.aquasuite.ims.mapper.sales.CartMapper;
import com.aquasuite.ims.repository.sales.CartItemRepository;
import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.service.crm.CustomerService;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.inventory.InventoryService;
import com.aquasuite.ims.service.sales.helper.CartComboSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Triển khai nghiệp vụ quản lý giỏ hàng của khách hàng (UC-17).
 * Hỗ trợ đầy đủ cả biến thể SKU đơn lẻ và gói Combo đồng bộ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final CartItemRepository cartItemRepository;
    private final CartMapper cartMapper;
    private final IamQueryService iamApi;
    private final CustomerService crmApi;
    private final CatalogLookupService catalogApi;
    private final InventoryService inventoryApi;

    @Transactional(readOnly = true)
    public CartResponse getCurrentCart() {
        UUID customerId = resolveCurrentCustomerId();
        return buildCartResponse(customerId);
    }

    @Transactional
    public CartResponse addItem(AddCartItemRequest request) {
        validatePositiveQuantity(request.getQuantity());
        UUID customerId = resolveCurrentCustomerId();

        if (request.getComboId() != null) {
            validateIntegerQuantity(request.getQuantity());
            UUID comboId = request.getComboId();
            CatalogBundleSummaryDto bundleSummary = catalogApi.getBundleSummary(comboId)
                    .orElseThrow(() -> new AppException(SalesErrorCode.COMBO_NOT_FOUND, comboId));
            ensureComboSellable(bundleSummary, Instant.now());

            BigDecimal targetQuantity = saveOrAccumulateComboCartItem(customerId, bundleSummary, request.getQuantity());
            log.info("Updated combo cart item for customerId={}, comboId={}, quantity={}",
                    customerId, comboId, targetQuantity);
            return buildCartResponse(customerId);
        }

        UUID variantId = request.getVariantId();
        if (variantId == null) {
            throw new AppException(SalesErrorCode.SKU_NOT_FOUND, "");
        }

        CatalogSkuSummaryDto skuSummary = catalogApi.getSkuSummary(variantId)
                .orElseThrow(() -> new AppException(SalesErrorCode.SKU_NOT_FOUND, variantId));

        ensureSkuActive(skuSummary);

        BigDecimal targetQuantity = saveOrAccumulateCartItem(customerId, skuSummary, request.getQuantity());

        log.info("Updated cart item for customerId={}, variantId={}, quantity={}",
                customerId, variantId, targetQuantity);
        return buildCartResponse(customerId);
    }

    @Transactional
    public CartResponse mergeGuestItems(MergeCartItemsRequest request) {
        UUID customerId = resolveCurrentCustomerId();
        Map<UUID, BigDecimal> aggregatedVariants = new LinkedHashMap<>();
        Map<UUID, BigDecimal> aggregatedCombos = new LinkedHashMap<>();

        for (AddCartItemRequest reqItem : request.getItems()) {
            if (reqItem == null || reqItem.getQuantity() == null
                    || reqItem.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (reqItem.getComboId() != null && reqItem.getVariantId() == null) {
                if (reqItem.getQuantity().stripTrailingZeros().scale() <= 0) {
                    aggregatedCombos.merge(reqItem.getComboId(), reqItem.getQuantity(), BigDecimal::add);
                }
            } else if (reqItem.getVariantId() != null && reqItem.getComboId() == null) {
                aggregatedVariants.merge(reqItem.getVariantId(), reqItem.getQuantity(), BigDecimal::add);
            }
        }

        if (aggregatedVariants.isEmpty() && aggregatedCombos.isEmpty()) {
            return buildCartResponse(customerId);
        }

        Map<UUID, CatalogSkuSummaryDto> skuMap = aggregatedVariants.isEmpty()
                ? Collections.emptyMap()
                : catalogApi.getSkuSummaries(aggregatedVariants.keySet());
        Map<UUID, CatalogBundleSummaryDto> bundleMap = aggregatedCombos.isEmpty()
                ? Collections.emptyMap()
                : catalogApi.getBundleSummaries(aggregatedCombos.keySet());

        Set<UUID> stockVariantIds = new LinkedHashSet<>(aggregatedVariants.keySet());
        collectBundleComponentVariantIds(bundleMap.values(), stockVariantIds);
        Map<UUID, StockSummaryDto> stockMap = stockVariantIds.isEmpty()
                ? Collections.emptyMap()
                : inventoryApi.getStockSummaries(stockVariantIds);

        for (Map.Entry<UUID, BigDecimal> entry : aggregatedVariants.entrySet()) {
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

        Instant now = Instant.now();
        for (Map.Entry<UUID, BigDecimal> entry : aggregatedCombos.entrySet()) {
            UUID comboId = entry.getKey();
            CatalogBundleSummaryDto bundle = bundleMap.get(comboId);
            if (!CartComboSupport.isComboActiveAndPublished(bundle, now)) {
                continue;
            }

            BigDecimal availableCombos = CartComboSupport.computeComboAvailableQty(bundle, stockMap);
            if (availableCombos.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Optional<CartItem> existingOpt = cartItemRepository.findByCustomerIdAndComboId(customerId, comboId);
            BigDecimal currentQty = existingOpt.map(CartItem::getQuantity).orElse(BigDecimal.ZERO);
            BigDecimal desiredTotal = currentQty.add(entry.getValue());
            BigDecimal cappedQty = desiredTotal.min(availableCombos);
            if (cappedQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal currentPrice = defaultAmount(bundle.packagePrice());
            CartItem cartItem = existingOpt.orElseGet(() -> CartItem.builder()
                    .customerId(customerId)
                    .comboId(comboId)
                    .quantity(BigDecimal.ZERO)
                    .unitPrice(currentPrice)
                    .build());
            cartItem.setQuantity(cappedQty);
            cartItem.setUnitPrice(currentPrice);
            cartItemRepository.save(cartItem);
        }

        log.info("Merged {} distinct variant items and {} combo items for customerId={}",
                aggregatedVariants.size(), aggregatedCombos.size(), customerId);
        return buildCartResponse(customerId);
    }

    @Transactional
    public CartResponse updateItemQuantity(UUID cartItemId, UpdateCartItemQuantityRequest request) {
        validatePositiveQuantity(request.getQuantity());
        UUID customerId = resolveCurrentCustomerId();

        CartItem cartItem = cartItemRepository.findByCartItemIdAndCustomerId(cartItemId, customerId)
                .orElseThrow(() -> new AppException(SalesErrorCode.CART_ITEM_NOT_FOUND));

        if (cartItem.getComboId() != null) {
            validateIntegerQuantity(request.getQuantity());
            UUID comboId = cartItem.getComboId();
            CatalogBundleSummaryDto bundleSummary = catalogApi.getBundleSummary(comboId)
                    .orElseThrow(() -> new AppException(SalesErrorCode.COMBO_NOT_FOUND, comboId));
            ensureComboSellable(bundleSummary, Instant.now());

            validateComboStockAvailability(bundleSummary, request.getQuantity());

            cartItem.setQuantity(request.getQuantity());
            cartItem.setUnitPrice(defaultAmount(bundleSummary.packagePrice()));
            cartItemRepository.save(cartItem);

            log.info("Updated combo cartItemId={} quantity={} for customerId={}",
                    cartItemId, request.getQuantity(), customerId);
            return buildCartResponse(customerId);
        }

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

    @Transactional
    public CartResponse removeItem(UUID cartItemId) {
        UUID customerId = resolveCurrentCustomerId();

        CartItem cartItem = cartItemRepository.findByCartItemIdAndCustomerId(cartItemId, customerId)
                .orElseThrow(() -> new AppException(SalesErrorCode.CART_ITEM_NOT_FOUND));

        cartItemRepository.delete(cartItem);
        log.info("Removed cartItemId={} for customerId={}", cartItemId, customerId);
        return buildCartResponse(customerId);
    }

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

    private void validateIntegerQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0
                || quantity.stripTrailingZeros().scale() > 0) {
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

    private BigDecimal saveOrAccumulateComboCartItem(
            UUID customerId,
            CatalogBundleSummaryDto bundleSummary,
            BigDecimal addQuantity
    ) {
        UUID comboId = bundleSummary.bundleId();
        BigDecimal currentPrice = defaultAmount(bundleSummary.packagePrice());

        Optional<CartItem> existingOpt = cartItemRepository.findByCustomerIdAndComboId(customerId, comboId);
        BigDecimal targetQuantity = existingOpt
                .map(existing -> existing.getQuantity().add(addQuantity))
                .orElse(addQuantity);

        validateComboStockAvailability(bundleSummary, targetQuantity);

        CartItem cartItem = existingOpt.orElseGet(() -> CartItem.builder()
                .customerId(customerId)
                .comboId(comboId)
                .quantity(BigDecimal.ZERO)
                .unitPrice(currentPrice)
                .build());
        cartItem.setQuantity(targetQuantity);
        cartItem.setUnitPrice(currentPrice);

        try {
            cartItemRepository.save(cartItem);
            return targetQuantity;
        } catch (DataIntegrityViolationException ex) {
            CartItem concurrentExisting = cartItemRepository.findByCustomerIdAndComboId(customerId, comboId)
                    .orElseThrow(() -> ex);
            BigDecimal retryTarget = concurrentExisting.getQuantity().add(addQuantity);
            validateComboStockAvailability(bundleSummary, retryTarget);
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

    private void ensureComboSellable(CatalogBundleSummaryDto bundleSummary, Instant now) {
        if (!CartComboSupport.isComboActiveAndPublished(bundleSummary, now)) {
            String ref = bundleSummary != null && bundleSummary.bundleName() != null
                    ? bundleSummary.bundleName()
                    : "";
            throw new AppException(SalesErrorCode.INVALID_COMBO, ref);
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

    private void validateComboStockAvailability(CatalogBundleSummaryDto bundle, BigDecimal requiredComboQty) {
        Set<UUID> compVariantIds = new LinkedHashSet<>();
        collectBundleComponentVariantIds(List.of(bundle), compVariantIds);
        Map<UUID, StockSummaryDto> stockMap = compVariantIds.isEmpty()
                ? Collections.emptyMap()
                : inventoryApi.getStockSummaries(compVariantIds);
        BigDecimal availableCombos = CartComboSupport.computeComboAvailableQty(bundle, stockMap);
        if (availableCombos.compareTo(requiredComboQty) < 0) {
            String code = bundle.bundleCode() != null ? bundle.bundleCode() : bundle.bundleName();
            throw new AppException(
                    SalesErrorCode.INSUFFICIENT_STOCK,
                    code + " (tồn khả dụng: " + availableCombos.stripTrailingZeros().toPlainString() + " gói)"
            );
        }
    }

    private void collectBundleComponentVariantIds(
            Iterable<CatalogBundleSummaryDto> bundles,
            Set<UUID> target
    ) {
        if (bundles == null) return;
        for (CatalogBundleSummaryDto bundle : bundles) {
            if (bundle == null || bundle.items() == null) continue;
            for (CatalogBundleSummaryDto.CatalogBundleItemDto item : bundle.items()) {
                if (item != null && item.variantId() != null) {
                    target.add(item.variantId());
                }
            }
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
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Set<UUID> comboIds = cartItems.stream()
                .map(CartItem::getComboId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Map<UUID, CatalogSkuSummaryDto> skuMap = variantIds.isEmpty()
                ? Collections.emptyMap()
                : catalogApi.getSkuSummaries(variantIds);
        Map<UUID, CatalogBundleSummaryDto> bundleMap = comboIds.isEmpty()
                ? Collections.emptyMap()
                : catalogApi.getBundleSummaries(comboIds);

        Set<UUID> stockVariantIds = new LinkedHashSet<>(variantIds);
        collectBundleComponentVariantIds(bundleMap.values(), stockVariantIds);
        Map<UUID, StockSummaryDto> stockMap = stockVariantIds.isEmpty()
                ? Collections.emptyMap()
                : inventoryApi.getStockSummaries(stockVariantIds);

        List<CartItemResponse> itemResponses = new ArrayList<>(cartItems.size());
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean hasInvalidItems = false;
        Instant now = Instant.now();

        for (CartItem item : cartItems) {
            if (item.getComboId() != null) {
                CatalogBundleSummaryDto bundle = bundleMap.get(item.getComboId());
                String skuCode = bundle != null && bundle.bundleCode() != null ? bundle.bundleCode() : "N/A";
                String productName = bundle != null && bundle.bundleName() != null
                        ? bundle.bundleName()
                        : "Combo không còn tồn tại";
                int componentCount = bundle != null && bundle.items() != null ? bundle.items().size() : 0;
                String variantName = componentCount > 0
                        ? "Combo trọn bộ (" + componentCount + " thiết bị)"
                        : "Combo trọn bộ";
                String imageUrl = bundle != null ? bundle.imageUrl() : null;
                List<String> components = CartComboSupport.formatComponents(bundle);

                BigDecimal storedUnitPrice = defaultAmount(item.getUnitPrice());
                BigDecimal currentSellingPrice = bundle != null && bundle.packagePrice() != null
                        ? bundle.packagePrice()
                        : storedUnitPrice;
                BigDecimal quantity = defaultAmount(item.getQuantity());
                BigDecimal lineTotal = currentSellingPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);

                BigDecimal availableQty = CartComboSupport.computeComboAvailableQty(bundle, stockMap);
                boolean isComboActive = CartComboSupport.isComboActiveAndPublished(bundle, now);
                boolean inStock = isComboActive && availableQty.compareTo(quantity) >= 0;
                boolean priceChanged = currentSellingPrice.compareTo(storedUnitPrice) != 0;

                if (!inStock || !isComboActive) {
                    hasInvalidItems = true;
                }

                totalQuantity = totalQuantity.add(quantity);
                subtotal = subtotal.add(lineTotal);

                itemResponses.add(cartMapper.toItemResponse(
                        item,
                        item.getComboId(),
                        skuCode,
                        productName,
                        variantName,
                        imageUrl,
                        "Gói",
                        currentSellingPrice,
                        lineTotal,
                        availableQty,
                        isComboActive ? ACTIVE_STATUS : "INACTIVE",
                        inStock,
                        priceChanged,
                        true,
                        components
                ));
                continue;
            }

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
                    priceChanged,
                    false,
                    null
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
