package com.aquasuite.ims.module.catalog.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.enums.*;
import com.aquasuite.ims.module.catalog.internal.payload.req.*;
import com.aquasuite.ims.module.catalog.internal.payload.res.BundleResponse;
import com.aquasuite.ims.module.catalog.internal.repository.*;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;
import com.aquasuite.ims.share.exception.AppException;

import jakarta.validation.Validation;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

class BundleEvaluationServiceTest {
    final UUID id = UUID.randomUUID(), sku = UUID.randomUUID(), parent = UUID.randomUUID();
    final Instant now = Instant.parse("2026-10-20T00:00:00Z");
    final ProductRepository products = mock(ProductRepository.class);
    final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    final BundleQueryService query = mock(BundleQueryService.class);
    final BundleInventoryReader inventory = mock(BundleInventoryReader.class);
    jakarta.validation.ValidatorFactory factory;
    BundleEvaluationService evaluation;
    ProductVariant variant;

    @BeforeEach
    void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        var detail =
                new BundleResponse(
                        id,
                        "CODE",
                        "Bundle",
                        "ACTIVE",
                        "PUBLISHED",
                        1L,
                        1,
                        new BigDecimal("110"),
                        new BigDecimal("95"),
                        new BigDecimal("15"),
                        new BigDecimal("13.6"),
                        null,
                        now,
                        now,
                        UUID.randomUUID(),
                        null,
                        null,
                        List.of(
                                new BundleResponse.Item(
                                        UUID.randomUUID(),
                                        sku,
                                        "SKU",
                                        "Item",
                                        2,
                                        new BigDecimal("55"),
                                        true)),
                        now.minusSeconds(1),
                        now.plusSeconds(60));
        when(query.getPublishedBundleDetails(id)).thenReturn(detail);
        variant = new ProductVariant();
        variant.setVariantId(sku);
        variant.setProductId(parent);
        variant.setStatus(VariantStatus.ACTIVE);
        variant.setSellingPrice(new BigDecimal("55"));
        variant.setSku("SKU");
        when(variants.findAllById(any())).thenReturn(List.of(variant));
        when(products.findAllById(any()))
                .thenReturn(
                        List.of(
                                Product.builder()
                                        .productId(parent)
                                        .productType(ProductType.NORMAL)
                                        .status(ProductStatus.ACTIVE)
                                        .build()));
        evaluation =
                new BundleEvaluationService(
                        products,
                        variants,
                        query,
                        new BundlePricingService(),
                        inventory,
                        factory.getValidator(),
                        Clock.fixed(now, ZoneOffset.UTC));
    }

    @AfterEach
    void close() {
        factory.close();
    }

    void stock(int available) {
        BigDecimal qty = BigDecimal.valueOf(available);
        when(inventory.read(any()))
                .thenReturn(
                        Map.of(
                                sku,
                                new StockSummaryDto(
                                        sku,
                                        "WH_MAIN",
                                        qty,
                                        BigDecimal.ZERO,
                                        qty,
                                        BigDecimal.ZERO)));
    }

    @Test
    void outageReturnsUnavailableWithoutInventingStock() {
        when(inventory.read(any())).thenThrow(new IllegalStateException("unavailable"));
        var r = evaluation.checkAvailability(id, new BundleEvaluationRequest(2, 1L));
        assertThat(r.availability()).isEqualTo("UNAVAILABLE");
        assertThat(r.purchasable()).isFalse();
        assertThat(r.items().getFirst().availableQty()).isNull();
        assertThat(r.items().getFirst().requiredQty()).isEqualTo(4);
        assertThat(r.totalPackagePrice()).isEqualByComparingTo("190");
    }

    @Test
    void fixedItemsUseBatchStockAndMultiplyDemand() {
        stock(4);
        var r = evaluation.checkAvailability(id, new BundleEvaluationRequest(2, 1L));
        assertThat(r.availability()).isEqualTo("AVAILABLE");
        assertThat(r.purchasable()).isTrue();
        assertThat(r.originalTotal()).isEqualByComparingTo("220");
        assertThat(r.savingAmount()).isEqualByComparingTo("30");
        verify(inventory).read(List.of(sku));
    }

    @Test
    void shortageNamesTheFixedSku() {
        stock(3);
        var r = evaluation.checkAvailability(id, new BundleEvaluationRequest(2, null));
        assertThat(r.availability()).isEqualTo("UNAVAILABLE");
        assertThat(r.missingVariantIds()).containsExactly(sku);
        assertThat(r.purchasable()).isFalse();
    }

    @Test
    void retailDropDoesNotReturnNegativeSavings() {
        stock(10);
        variant.setSellingPrice(BigDecimal.ONE);
        var r = evaluation.checkAvailability(id, new BundleEvaluationRequest(1, null));
        assertThat(r.priceIssue()).isEqualTo("PACKAGE_EXCEEDS_RETAIL");
        assertThat(r.savingAmount()).isNull();
        assertThat(r.purchasable()).isFalse();
    }

    @Test
    void inactiveSkuIsNotPurchasableEvenWithStock() {
        stock(10);
        variant.setStatus(VariantStatus.INACTIVE);
        var r = evaluation.checkAvailability(id, new BundleEvaluationRequest(1, null));
        assertThat(r.priceIssue()).isEqualTo("COMPONENT_UNAVAILABLE");
        assertThat(r.purchasable()).isFalse();
    }

    @Test
    void staleVersionDoesNotReadInventory() {
        assertThatThrownBy(
                        () -> evaluation.checkAvailability(id, new BundleEvaluationRequest(1, 0L)))
                .isInstanceOfSatisfying(
                        AppException.class,
                        e ->
                                assertThat(e.getErrorCode().getCode())
                                        .isEqualTo("CATALOG_BUNDLE_003"));
        verifyNoInteractions(inventory);
    }

    @Test
    void invalidQuantityIsRejectedBeforeQueries() {
        assertThatThrownBy(
                        () ->
                                evaluation.checkAvailability(
                                        id, new BundleEvaluationRequest(0, null)))
                .isInstanceOf(AppException.class);
        verifyNoInteractions(inventory, query);
    }
}
