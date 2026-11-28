package com.aquasuite.ims.service.sales.impl;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.dto.catalog.response.CatalogBundleSummaryDto;
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
import com.aquasuite.ims.dto.sales.response.CartResponse;
import com.aquasuite.ims.repository.sales.CartItemRepository;
import com.aquasuite.ims.exception.AppException;
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
    private IamQueryService iamApi;

    @Mock
    private CustomerService crmApi;

    @Mock
    private CatalogLookupService catalogApi;

    @Mock
    private InventoryService inventoryApi;

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
                .productStatus("ACTIVE")
                .imageUrl("https://images.unsplash.com/photo-1507652313519-d4e9174996dd?w=600")
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
        assertThat(response.getItems().get(0).getImageUrl())
                .isEqualTo("https://images.unsplash.com/photo-1507652313519-d4e9174996dd?w=600");
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

    @Test
    void addItem_inactiveParentProduct_throwsSkuNotActive() {
        mockAuthenticatedCustomer();
        CatalogSkuSummaryDto discontinuedParent = CatalogSkuSummaryDto.builder()
                .variantId(variantId)
                .productId(productId)
                .sku("SEN-TOTO-01")
                .productName("Sen cây TOTO")
                .variantName("Mạ Crom")
                .unit("Bộ")
                .sellingPrice(new BigDecimal("2500000"))
                .status("ACTIVE")
                .productStatus("DISCONTINUED")
                .build();

        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(discontinuedParent));

        assertThatThrownBy(() -> cartService.addItem(AddCartItemRequest.builder()
                .variantId(variantId)
                .quantity(new BigDecimal("1"))
                .build()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(SalesErrorCode.SKU_NOT_ACTIVE);
    }

    @Test
    void mergeGuestItems_capsQuantityAtAvailableStockAndSkipsInactiveItems() {
        mockAuthenticatedCustomer();
        UUID inactiveVariantId = UUID.randomUUID();
        CatalogSkuSummaryDto activeSku = buildActiveSku(new BigDecimal("2000"));
        CatalogSkuSummaryDto inactiveSku = CatalogSkuSummaryDto.builder()
                .variantId(inactiveVariantId)
                .productId(UUID.randomUUID())
                .sku("OLD-SKU")
                .status("INACTIVE")
                .build();
        StockSummaryDto stock = buildStock(new BigDecimal("4"));

        when(catalogApi.getSkuSummaries(any())).thenReturn(Map.of(
                variantId, activeSku,
                inactiveVariantId, inactiveSku
        ));
        when(inventoryApi.getStockSummaries(any())).thenReturn(Map.of(variantId, stock));
        when(cartItemRepository.findByCustomerIdAndVariantId(customerId, variantId)).thenReturn(Optional.empty());
        when(cartItemRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId)).thenReturn(List.of());

        cartService.mergeGuestItems(MergeCartItemsRequest.builder()
                .items(List.of(
                        AddCartItemRequest.builder().variantId(variantId).quantity(new BigDecimal("10")).build(),
                        AddCartItemRequest.builder().variantId(inactiveVariantId).quantity(new BigDecimal("2")).build()
                ))
                .build());

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        assertThat(captor.getValue().getVariantId()).isEqualTo(variantId);
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("4");
    }

    @Test
    void addItem_comboSuccess_computesComboStockAndReturnsComboCartLine() {
        mockAuthenticatedCustomer();
        UUID comboId = UUID.randomUUID();
        UUID comp2Id = UUID.randomUUID();

        CatalogBundleSummaryDto bundle = CatalogBundleSummaryDto.builder()
                .bundleId(comboId)
                .bundleCode("CB-HOTEL-4STAR")
                .bundleName("Combo Khách sạn 4 Sao")
                .packagePrice(new BigDecimal("18500000"))
                .status("ACTIVE")
                .publicationStatus("PUBLISHED")
                .imageUrl("https://example.com/combo.jpg")
                .items(List.of(
                        CatalogBundleSummaryDto.CatalogBundleItemDto.builder()
                                .variantId(variantId)
                                .sku("TOTO-LT4706-W")
                                .variantName("Chậu rửa TOTO")
                                .quantity(1)
                                .sellingPrice(new BigDecimal("3200000"))
                                .sellable(true)
                                .build(),
                        CatalogBundleSummaryDto.CatalogBundleItemDto.builder()
                                .variantId(comp2Id)
                                .sku("TOTO-CS886-B")
                                .variantName("Bồn cầu TOTO")
                                .quantity(2)
                                .sellingPrice(new BigDecimal("17500000"))
                                .sellable(true)
                                .build()
                ))
                .build();

        when(catalogApi.getBundleSummary(comboId)).thenReturn(Optional.of(bundle));
        when(catalogApi.getBundleSummaries(any())).thenReturn(Map.of(comboId, bundle));
        when(cartItemRepository.findByCustomerIdAndComboId(customerId, comboId)).thenReturn(Optional.empty());
        when(inventoryApi.getStockSummaries(any())).thenReturn(Map.of(
                variantId, StockSummaryDto.builder().variantId(variantId).availableQty(new BigDecimal("10")).build(),
                comp2Id, StockSummaryDto.builder().variantId(comp2Id).availableQty(new BigDecimal("5")).build()
        ));

        CartItem savedCombo = CartItem.builder()
                .cartItemId(UUID.randomUUID())
                .customerId(customerId)
                .comboId(comboId)
                .quantity(new BigDecimal("2"))
                .unitPrice(new BigDecimal("18500000"))
                .updatedAt(OffsetDateTime.now())
                .build();
        when(cartItemRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId)).thenReturn(List.of(savedCombo));

        CartResponse response = cartService.addItem(AddCartItemRequest.builder()
                .comboId(comboId)
                .quantity(new BigDecimal("2"))
                .build());

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).isCombo()).isTrue();
        assertThat(response.getItems().get(0).getComboId()).isEqualTo(comboId);
        assertThat(response.getItems().get(0).getVariantId()).isNull();
        assertThat(response.getItems().get(0).getAvailableQty()).isEqualByComparingTo("2");
        assertThat(response.getItems().get(0).getUnit()).isEqualTo("Gói");
        assertThat(response.getItems().get(0).getComponents()).hasSize(2);
        assertThat(response.getSubtotal()).isEqualByComparingTo("37000000.00");
    }

    @Test
    void addItem_comboInsufficientComponentStock_throwsInsufficientStock() {
        mockAuthenticatedCustomer();
        UUID comboId = UUID.randomUUID();
        CatalogBundleSummaryDto bundle = CatalogBundleSummaryDto.builder()
                .bundleId(comboId)
                .bundleCode("CB-HOTEL-4STAR")
                .bundleName("Combo Khách sạn 4 Sao")
                .packagePrice(new BigDecimal("18500000"))
                .status("ACTIVE")
                .publicationStatus("PUBLISHED")
                .items(List.of(
                        CatalogBundleSummaryDto.CatalogBundleItemDto.builder()
                                .variantId(variantId)
                                .sku("TOTO-LT4706-W")
                                .variantName("Chậu rửa TOTO")
                                .quantity(1)
                                .sellingPrice(new BigDecimal("3200000"))
                                .sellable(true)
                                .build()
                ))
                .build();

        when(catalogApi.getBundleSummary(comboId)).thenReturn(Optional.of(bundle));
        when(cartItemRepository.findByCustomerIdAndComboId(customerId, comboId)).thenReturn(Optional.empty());
        when(inventoryApi.getStockSummaries(any())).thenReturn(Map.of(
                variantId, StockSummaryDto.builder().variantId(variantId).availableQty(BigDecimal.ZERO).build()
        ));

        assertThatThrownBy(() -> cartService.addItem(AddCartItemRequest.builder()
                .comboId(comboId)
                .quantity(BigDecimal.ONE)
                .build()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(SalesErrorCode.INSUFFICIENT_STOCK);
    }
}

