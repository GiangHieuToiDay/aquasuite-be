package com.aquasuite.ims.module.catalog.internal.variant;

import com.aquasuite.ims.module.catalog.api.event.ProductVariantChangedEvent;
import com.aquasuite.ims.module.catalog.internal.entity.Product;
import com.aquasuite.ims.module.catalog.internal.entity.ProductVariant;
import com.aquasuite.ims.module.catalog.internal.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.enums.VariantStatus;
import com.aquasuite.ims.module.catalog.internal.error.CatalogVariantErrorCode;
import com.aquasuite.ims.module.catalog.internal.error.VariantDeleteBlockedException;
import com.aquasuite.ims.module.catalog.internal.mapper.VariantMapper;
import com.aquasuite.ims.module.catalog.internal.repository.ProductRepository;
import com.aquasuite.ims.module.catalog.internal.repository.ProductVariantRepository;
import com.aquasuite.ims.module.catalog.internal.payload.req.VariantDeleteRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.VariantUpsertRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.VariantResponse;
import com.aquasuite.ims.module.catalog.internal.service.impl.VariantServiceImpl;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.VariantInventoryUsage;
import com.aquasuite.ims.module.sales.api.SalesVariantUsageApi;
import com.aquasuite.ims.module.sales.api.dto.VariantDocumentUsage;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
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
    @Mock private InventoryApi inventoryApi;
    @Mock private SalesVariantUsageApi salesVariantUsageApi;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private IamApi iamApi;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private VariantMapper variantMapper;

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
