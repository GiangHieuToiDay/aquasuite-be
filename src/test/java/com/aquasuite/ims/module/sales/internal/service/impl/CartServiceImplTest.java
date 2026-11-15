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
import com.aquasuite.ims.module.sales.internal.payload.response.CartResponse;
import com.aquasuite.ims.module.sales.internal.repository.CartItemRepository;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private IamApi iamApi;

    @Mock
    private CrmApi crmApi;

    @Mock
    private CatalogApi catalogApi;

    @Mock
    private InventoryApi inventoryApi;

    private final CartMapper cartMapper = Mappers.getMapper(CartMapper.class);

    private CartServiceImpl cartService;

    private UUID userId;
    private UUID customerId;
    private UUID variantId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        cartService = new CartServiceImpl(
                cartItemRepository,
                cartMapper,
                iamApi,
                crmApi,
                catalogApi,
                inventoryApi
        );
        userId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        productId = UUID.randomUUID();
    }

    private void mockAuthenticatedCustomer() {
        when(iamApi.getCurrentUserId()).thenReturn(userId);
        when(crmApi.getCustomerByUserId(userId)).thenReturn(Optional.of(
                CustomerSummaryDto.builder()
                        .customerId(customerId)
                        .userId(userId)
                        .customerCode("CUST-001")
                        .customerName("Lâm Nguyễn")
                        .status("ACTIVE")
                        .build()
        ));
    }

    private CatalogSkuSummaryDto buildActiveSku(BigDecimal sellingPrice) {
        return CatalogSkuSummaryDto.builder()
                .variantId(variantId)
                .productId(productId)
                .sku("SEN-TOTO-01")
                .productName("Sen cây TOTO")
                .variantName("Mạ Crom")
                .unit("Bộ")
                .sellingPrice(sellingPrice)
                .status("ACTIVE")
                .build();
    }

    private StockSummaryDto buildStock(BigDecimal availableQty) {
        return StockSummaryDto.builder()
                .variantId(variantId)
                .availableQty(availableQty)
                .onHandQty(availableQty)
                .reservedQty(BigDecimal.ZERO)
                .build();
    }

    @Test
    void addItem_newVariant_createsCartItemAndReturnsEnrichedCart() {
        mockAuthenticatedCustomer();
        CatalogSkuSummaryDto sku = buildActiveSku(new BigDecimal("2500000"));
        StockSummaryDto stock = buildStock(new BigDecimal("10"));

        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(sku));
        when(inventoryApi.getStockSummary(variantId)).thenReturn(Optional.of(stock));
        when(cartItemRepository.findByCustomerIdAndVariantId(customerId, variantId)).thenReturn(Optional.empty());

        CartItem savedItem = CartItem.builder()
                .cartItemId(UUID.randomUUID())
                .customerId(customerId)
                .variantId(variantId)
                .quantity(new BigDecimal("2"))
                .unitPrice(new BigDecimal("2500000"))
                .updatedAt(OffsetDateTime.now())
                .build();
        when(cartItemRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId)).thenReturn(List.of(savedItem));
        when(catalogApi.getSkuSummaries(any())).thenReturn(Map.of(variantId, sku));
        when(inventoryApi.getStockSummaries(any())).thenReturn(Map.of(variantId, stock));

        CartResponse response = cartService.addItem(AddCartItemRequest.builder()
                .variantId(variantId)
                .quantity(new BigDecimal("2"))
                .build());

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("2");
        assertThat(captor.getValue().getUnitPrice()).isEqualByComparingTo("2500000");

        assertThat(response.getTotalItems()).isEqualTo(1);
        assertThat(response.getTotalQuantity()).isEqualByComparingTo("2");
        assertThat(response.getSubtotal()).isEqualByComparingTo("5000000.00");
        assertThat(response.isHasInvalidItems()).isFalse();
        assertThat(response.getItems().get(0).getSku()).isEqualTo("SEN-TOTO-01");
    }

    @Test
    void addItem_existingVariant_accumulatesQuantity() {
        mockAuthenticatedCustomer();
        CatalogSkuSummaryDto sku = buildActiveSku(new BigDecimal("1500000"));
        StockSummaryDto stock = buildStock(new BigDecimal("10"));

        CartItem existing = CartItem.builder()
                .cartItemId(UUID.randomUUID())
                .customerId(customerId)
                .variantId(variantId)
                .quantity(new BigDecimal("3"))
                .unitPrice(new BigDecimal("1500000"))
                .updatedAt(OffsetDateTime.now())
                .build();

        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(sku));
        when(inventoryApi.getStockSummary(variantId)).thenReturn(Optional.of(stock));
        when(cartItemRepository.findByCustomerIdAndVariantId(customerId, variantId)).thenReturn(Optional.of(existing));
        when(cartItemRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId)).thenReturn(List.of(existing));
        when(catalogApi.getSkuSummaries(any())).thenReturn(Map.of(variantId, sku));
        when(inventoryApi.getStockSummaries(any())).thenReturn(Map.of(variantId, stock));

        CartResponse response = cartService.addItem(AddCartItemRequest.builder()
                .variantId(variantId)
                .quantity(new BigDecimal("2"))
                .build());

        assertThat(existing.getQuantity()).isEqualByComparingTo("5");
        assertThat(response.getTotalQuantity()).isEqualByComparingTo("5");
        assertThat(response.getSubtotal()).isEqualByComparingTo("7500000.00");
    }

    @Test
    void addItem_exceedsAvailableStock_throwsInsufficientStock() {
        mockAuthenticatedCustomer();
        CatalogSkuSummaryDto sku = buildActiveSku(new BigDecimal("1500000"));
        StockSummaryDto stock = buildStock(new BigDecimal("3"));

        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(sku));
        when(inventoryApi.getStockSummary(variantId)).thenReturn(Optional.of(stock));
        when(cartItemRepository.findByCustomerIdAndVariantId(customerId, variantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(AddCartItemRequest.builder()
                .variantId(variantId)
                .quantity(new BigDecimal("5"))
                .build()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(SalesErrorCode.INSUFFICIENT_STOCK);
    }

    @Test
    void updateItemQuantity_notFound_throwsCartItemNotFound() {
        mockAuthenticatedCustomer();
        UUID cartItemId = UUID.randomUUID();
        when(cartItemRepository.findByCartItemIdAndCustomerId(cartItemId, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateItemQuantity(
                cartItemId,
                UpdateCartItemQuantityRequest.builder().quantity(new BigDecimal("2")).build()
        ))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(SalesErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    void clearCart_deletesAllCustomerItemsAndReturnsEmptyCart() {
        mockAuthenticatedCustomer();
        when(cartItemRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId)).thenReturn(List.of());

        CartResponse response = cartService.clearCart();

        verify(cartItemRepository).deleteAllByCustomerId(customerId);
        assertThat(response.getTotalItems()).isZero();
        assertThat(response.getItems()).isEmpty();
        assertThat(response.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}

