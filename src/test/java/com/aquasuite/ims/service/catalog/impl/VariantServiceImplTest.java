package com.aquasuite.ims.service.catalog.impl;

import com.aquasuite.ims.event.catalog.ProductVariantChangedEvent;
import com.aquasuite.ims.entity.catalog.Product;
import com.aquasuite.ims.entity.catalog.ProductVariant;
import com.aquasuite.ims.entity.catalog.WarrantyPolicy;
import com.aquasuite.ims.enums.catalog.ProductStatus;
import com.aquasuite.ims.enums.catalog.ProductType;
import com.aquasuite.ims.enums.catalog.VariantStatus;
import com.aquasuite.ims.exception.catalog.CatalogVariantErrorCode;
import com.aquasuite.ims.exception.catalog.VariantDeleteBlockedException;
import com.aquasuite.ims.repository.catalog.ProductRepository;
import com.aquasuite.ims.repository.catalog.ProductVariantRepository;
import com.aquasuite.ims.repository.catalog.WarrantyPolicyRepository;
import com.aquasuite.ims.dto.catalog.request.VariantDeleteRequest;
import com.aquasuite.ims.dto.catalog.request.VariantUpsertRequest;
import com.aquasuite.ims.dto.catalog.response.VariantResponse;
import com.aquasuite.ims.service.catalog.impl.VariantServiceImpl;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.dto.iam.response.UserSummaryDto;
import com.aquasuite.ims.service.inventory.InventoryService;
import com.aquasuite.ims.dto.inventory.response.StockSummaryDto;
import com.aquasuite.ims.dto.inventory.response.VariantInventoryUsage;
import com.aquasuite.ims.service.sales.SalesQueryService;
import com.aquasuite.ims.dto.sales.response.VariantDocumentUsage;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VariantServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private WarrantyPolicyRepository warrantyPolicyRepository;
    @Mock private InventoryService inventoryApi;
    @Mock private SalesQueryService salesVariantUsageApi;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private IamQueryService iamApi;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private VariantServiceImpl service;
    private UUID productId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        Product product = new Product();
        product.setProductId(productId);
        product.setStatus(ProductStatus.ACTIVE);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        // deferred to test methods that need it
    }

    @Test
    void create_withoutRetailPrice_createsInactiveSkuAndMainWarehouseInventory() {
        when(iamApi.getCurrentUserId()).thenReturn(UUID.randomUUID());
        UUID variantId = UUID.randomUUID();
        when(variantRepository.findBySkuIgnoreCase("SKU-NEO-WHT-305")).thenReturn(Optional.empty());
        when(variantRepository.saveAndFlush(any(ProductVariant.class))).thenAnswer(invocation -> {
            ProductVariant variant = invocation.getArgument(0);
            variant.setVariantId(variantId);
            return variant;
        });

        VariantResponse result = service.create(productId, request(null, new BigDecimal("45000000"), false));

        assertThat(result.status()).isEqualTo(VariantStatus.INACTIVE.name());
        assertThat(result.sellingPrice()).isNull();
        verify(inventoryApi).initializeMainWarehouseInventory(variantId);
        ArgumentCaptor<ProductVariantChangedEvent> event = ArgumentCaptor.forClass(ProductVariantChangedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().action()).isEqualTo("CREATED");
        assertThat(event.getValue().newData()).doesNotContainKeys("serialNumber", "password");
    }

    @Test
    void create_belowCostWithoutAcknowledgement_rejectsBeforePersistence() {
        assertThatThrownBy(() -> service.create(productId,
                request(new BigDecimal("44000000"), new BigDecimal("45000000"), false)))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(CatalogVariantErrorCode.PRICE_WARNING_ACK_REQUIRED);

        verify(variantRepository, never()).saveAndFlush(any(ProductVariant.class));
        verify(inventoryApi, never()).initializeMainWarehouseInventory(any());
    }

    @Test
    void create_rejectsLegacyDeletedStatus() {
        VariantUpsertRequest request = new VariantUpsertRequest("SKU-NEO-WHT-305", "White 305", "cái",
                BigDecimal.ONE, "White", "305mm", BigDecimal.ZERO, Map.of(), BigDecimal.ZERO,
                new BigDecimal("100000"), null, false, VariantStatus.DELETED, true);

        assertThatThrownBy(() -> service.create(productId, request))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(CatalogVariantErrorCode.INVALID_STATUS);
        verify(variantRepository, never()).saveAndFlush(any(ProductVariant.class));
    }

    @Test
    void create_inheritsProductSpecificationsAndTheActiveBrandCategoryPolicy() {
        UUID brandId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Product product = new Product();
        product.setProductId(productId);
        product.setStatus(ProductStatus.ACTIVE);
        product.setProductType(ProductType.NORMAL);
        product.setBrandId(brandId);
        product.setCategoryId(categoryId);
        product.setSpecifications(Map.of("roughIn", "305 mm", "flush", "3/6 L"));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(variantRepository.findBySkuIgnoreCase("SKU-NEO-WHT-305")).thenReturn(Optional.empty());
        WarrantyPolicy policy = new WarrantyPolicy();
        policy.setWarrantyPolicyId(policyId);
        when(warrantyPolicyRepository.findFirstByBrandIdAndCategoryIdAndStatus(brandId, categoryId, "ACTIVE"))
                .thenReturn(Optional.of(policy));
        when(variantRepository.saveAndFlush(any(ProductVariant.class))).thenAnswer(invocation -> {
            ProductVariant variant = invocation.getArgument(0);
            variant.setVariantId(variantId);
            return variant;
        });
        when(iamApi.getCurrentUserId()).thenReturn(UUID.randomUUID());

        VariantResponse result = service.create(productId, request(new BigDecimal("50000000"), new BigDecimal("45000000"), false));

        assertThat(result.technicalSpec()).containsEntry("roughIn", "305mm").containsEntry("flush", "3/6 L");
        assertThat(result.warrantyPolicyId()).isEqualTo(policyId);
    }

    @Test
    void create_rejectsPhysicalSkuForBundleProduct() {
        Product bundle = new Product();
        bundle.setProductId(productId);
        bundle.setStatus(ProductStatus.ACTIVE);
        bundle.setProductType(ProductType.BUNDLE);
        when(productRepository.findById(productId)).thenReturn(Optional.of(bundle));

        assertThatThrownBy(() -> service.create(productId, request(new BigDecimal("50000000"), new BigDecimal("45000000"), false)))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(CatalogVariantErrorCode.BUNDLE_VARIANT_NOT_ALLOWED);
        verify(variantRepository, never()).saveAndFlush(any(ProductVariant.class));
    }

    @Test
    void list_returnsWarehouseSpecificStockAndSupportsMissingPriceFilter() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = variant(variantId);
        variant.setSellingPrice(null);
        variant.setCreatedAt(java.time.Instant.now());
        when(variantRepository.search(eq(productId), eq(null), eq(null), eq(true), any()))
                .thenReturn(new PageImpl<>(java.util.List.of(variant), PageRequest.of(0, 10), 1));
        StockSummaryDto stock = new StockSummaryDto(variantId, "WH_MAIN", new BigDecimal("12"),
                new BigDecimal("3"), new BigDecimal("9"), new BigDecimal("5"));
        when(inventoryApi.getStockSummaries(java.util.List.of(variantId), "WH_MAIN"))
                .thenReturn(Map.of(variantId, stock));

        var page = service.list(productId, null, null, true, "sku", "ASC", "WH_MAIN", PageRequest.of(0, 10));

        assertThat(page.getContent()).singleElement().satisfies(result -> {
            assertThat(result.onHandQty()).isEqualByComparingTo("12");
            assertThat(result.reservedQty()).isEqualByComparingTo("3");
            assertThat(result.availableQty()).isEqualByComparingTo("9");
            assertThat(result.lowStock()).isFalse();
        });
    }

    @Test
    void list_masksCostPriceAndRejectsProfitOrCostSortForSalesStaff() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = variant(variantId);
        variant.setCostPrice(new BigDecimal("30000000"));
        variant.setSellingPrice(new BigDecimal("45000000"));
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder()
                .id(UUID.randomUUID())
                .username("sales.staff")
                .roles(List.of("SALES_STAFF"))
                .build());
        when(variantRepository.search(eq(productId), eq(null), eq(null), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(variant), PageRequest.of(0, 10), 1));

        var page = service.list(productId, null, null, false, "sku", "ASC", "WH_MAIN", PageRequest.of(0, 10));

        assertThat(page.getContent()).singleElement().satisfies(result -> {
            assertThat(result.costPrice()).isNull();
            assertThat(result.sellingPrice()).isEqualByComparingTo("45000000");
        });

        assertThatThrownBy(() -> service.list(productId, null, null, false, "profit", "DESC", "WH_MAIN", PageRequest.of(0, 10)))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);

        assertThatThrownBy(() -> service.list(productId, null, null, false, "costPrice", "ASC", "WH_MAIN", PageRequest.of(0, 10)))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    void list_includesCostPriceAndAllowsProfitSortForAccountantAndShopOwner() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = variant(variantId);
        variant.setCostPrice(new BigDecimal("30000000"));
        variant.setSellingPrice(new BigDecimal("45000000"));
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder()
                .id(UUID.randomUUID())
                .username("accountant")
                .roles(List.of("ACCOUNTANT"))
                .build());
        when(variantRepository.searchByProfit(eq(productId), eq(null), eq(null), eq(false), eq("DESC"), any()))
                .thenReturn(new PageImpl<>(List.of(variant), PageRequest.of(0, 10), 1));

        var page = service.list(productId, null, null, false, "profit", "DESC", "WH_MAIN", PageRequest.of(0, 10));

        assertThat(page.getContent()).singleElement().satisfies(result -> {
            assertThat(result.costPrice()).isEqualByComparingTo("30000000");
            assertThat(result.sellingPrice()).isEqualByComparingTo("45000000");
        });
    }

    @Test
    void deletionEligibility_marksDocumentHistoryAsBlocked() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = new ProductVariant();
        variant.setVariantId(variantId);
        variant.setProductId(productId);
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(inventoryApi.getVariantDeletionUsage(variantId)).thenReturn(
                new VariantInventoryUsage(variantId, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0));
        when(salesVariantUsageApi.getVariantDocumentUsage(variantId)).thenReturn(
                new VariantDocumentUsage(variantId, 2, 0, 0, 0, 0, 0, 0, 0));
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), eq(variantId))).thenReturn(0L);

        var result = service.deletionEligibility(productId, variantId);

        assertThat(result.eligible()).isFalse();
        assertThat(result.goodsReceiptCount()).isEqualTo(2);
    }

    @Test
    void delete_purchaseOrderReferenceBlocksAfterVariantAndInventoryLocks() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = variant(variantId);
        when(variantRepository.findByProductIdAndVariantIdForUpdate(productId, variantId)).thenReturn(Optional.of(variant));
        when(inventoryApi.lockAndGetVariantDeletionUsage(variantId)).thenReturn(
                new VariantInventoryUsage(variantId, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0));
        when(salesVariantUsageApi.getVariantDocumentUsage(variantId)).thenReturn(
                new VariantDocumentUsage(variantId, 0, 0, 0, 0, 0, 1, 0, 0));

        assertThatThrownBy(() -> service.delete(productId, variantId, new VariantDeleteRequest(true, "created in error")))
                .isInstanceOf(VariantDeleteBlockedException.class)
                .extracting(error -> ((VariantDeleteBlockedException) error).getEligibility().purchaseOrderCount())
                .isEqualTo(1L);

        verify(inventoryApi).lockAndGetVariantDeletionUsage(variantId);
        verify(salesVariantUsageApi, never()).removeVariantFromCarts(variantId);
        verify(variantRepository, never()).delete(any(ProductVariant.class));
    }

    @Test
    void delete_activeBundleComponentOptionBlocksDeletion() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = variant(variantId);
        when(variantRepository.findByProductIdAndVariantIdForUpdate(productId, variantId)).thenReturn(Optional.of(variant));
        when(inventoryApi.lockAndGetVariantDeletionUsage(variantId)).thenReturn(
                new VariantInventoryUsage(variantId, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0));
        when(salesVariantUsageApi.getVariantDocumentUsage(variantId)).thenReturn(
                new VariantDocumentUsage(variantId, 0, 0, 0, 0, 0, 0, 0, 0));
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class).contains("bundle_component_groups") ? 1L : 0L);

        assertThatThrownBy(() -> service.delete(productId, variantId, new VariantDeleteRequest(true, "created in error")))
                .isInstanceOf(VariantDeleteBlockedException.class)
                .extracting(error -> ((VariantDeleteBlockedException) error).getEligibility().activeBundleOptionCount())
                .isEqualTo(1L);

        verify(salesVariantUsageApi, never()).removeVariantFromCarts(variantId);
        verify(variantRepository, never()).delete(any(ProductVariant.class));
    }

    @Test
    void delete_eligibleVariantRemovesDependentsAndPublishesEligibilitySnapshot() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = variant(variantId);
        when(variantRepository.findByProductIdAndVariantIdForUpdate(productId, variantId)).thenReturn(Optional.of(variant));
        when(inventoryApi.lockAndGetVariantDeletionUsage(variantId)).thenReturn(
                new VariantInventoryUsage(variantId, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0));
        when(salesVariantUsageApi.getVariantDocumentUsage(variantId)).thenReturn(
                new VariantDocumentUsage(variantId, 0, 0, 0, 0, 0, 0, 0, 2));
        when(iamApi.getCurrentUserId()).thenReturn(UUID.randomUUID());

        service.delete(productId, variantId, new VariantDeleteRequest(true, "created in error"));

        verify(salesVariantUsageApi).removeVariantFromCarts(variantId);
        verify(inventoryApi).deleteEmptyVariantInventory(variantId);
        verify(variantRepository).delete(variant);
        verify(variantRepository).flush();
        ArgumentCaptor<ProductVariantChangedEvent> event = ArgumentCaptor.forClass(ProductVariantChangedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().oldData()).containsKey("deletionEligibility");
        @SuppressWarnings("unchecked")
        Map<String, Object> deletionEligibility = (Map<String, Object>) event.getValue().oldData().get("deletionEligibility");
        assertThat(deletionEligibility)
                .containsEntry("cartItemCount", 2L)
                .containsEntry("purchaseOrderCount", 0L)
                .containsEntry("deliveryItemCount", 0L);
    }

    private ProductVariant variant(UUID variantId) {
        ProductVariant variant = new ProductVariant();
        variant.setVariantId(variantId);
        variant.setProductId(productId);
        variant.setSku("SKU-NEO-WHT-305");
        variant.setVariantName("White 305");
        variant.setStatus(VariantStatus.ACTIVE);
        variant.setCostPrice(BigDecimal.ZERO);
        return variant;
    }

    private VariantUpsertRequest request(BigDecimal sellingPrice, BigDecimal costPrice, boolean acknowledgeBelowCost) {
        return new VariantUpsertRequest("SKU-NEO-WHT-305", "White 305", "cái", BigDecimal.ONE,
                "Glossy White", "305mm", BigDecimal.ZERO, Map.of("roughIn", "305mm"), costPrice,
                sellingPrice, null, false, null, acknowledgeBelowCost);
    }
}
