package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.entity.enums.*;
import com.aquasuite.ims.module.catalog.internal.payload.bundle.request.*;
import com.aquasuite.ims.module.catalog.internal.payload.bundle.response.*;
import com.aquasuite.ims.module.catalog.internal.repository.*;
import com.aquasuite.ims.module.inventory.api.InventoryAvailabilityApi;
import com.aquasuite.ims.share.exception.AppException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BundleStorefrontServiceTest {
    final UUID id = UUID.randomUUID(), sku = UUID.randomUUID(), parent = UUID.randomUUID();
    final Instant from = Instant.parse("2026-10-20T00:00:00Z"), until = from.plusSeconds(60);
    final ProductRepository products = mock(ProductRepository.class);
    final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    final BundleConfigurationRepository configs = mock(BundleConfigurationRepository.class);
    final BundleAdminService admin = mock(BundleAdminService.class);
    final InventoryAvailabilityApi inventory = mock(InventoryAvailabilityApi.class);
    final BundleConfiguration config = new BundleConfiguration();
    BundleResponse detail;
    jakarta.validation.ValidatorFactory validatorFactory;

    @BeforeEach
    void setup() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        config.setPublicationStatus(BundleConfiguration.PublicationStatus.PUBLISHED);
        config.setValidFrom(from);
        config.setValidUntil(until);
        detail =
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
                        from,
                        from,
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
                        from,
                        until,
                        List.of());
        when(configs.findById(id)).thenReturn(Optional.of(config));
        when(admin.get(id)).thenReturn(detail);
        var variant = new ProductVariant();
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
    }

    @AfterEach
    void close() {
        validatorFactory.close();
    }

    BundleStorefrontService at(Instant instant) {
        return new BundleStorefrontService(
                products,
                variants,
                configs,
                admin,
                new BundlePricingService(),
                inventory,
                validatorFactory.getValidator(),
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    @ParameterizedTest
    @CsvSource({"-1,false", "0,true", "1,true", "59,true", "60,false", "61,false"})
    void visibilityUsesInclusiveStartExclusiveEnd(long offset, boolean visible) {
        if (visible) assertThat(at(from.plusSeconds(offset)).get(id)).isSameAs(detail);
        else
            assertThatThrownBy(() -> at(from.plusSeconds(offset)).get(id))
                    .isInstanceOf(AppException.class);
    }

    @Test
    void inventoryOutageIsUnknownAndCannotConfirmPurchasable() {
        when(inventory.availableQuantities(any()))
                .thenThrow(new IllegalStateException("unavailable"));
        var result = at(from).evaluate(id, new BundleEvaluationRequest(2, 1L, List.of()));
        assertThat(result.availability()).isEqualTo("UNKNOWN");
        assertThat(result.purchasable()).isFalse();
        assertThat(result.items().getFirst().availableQty()).isNull();
        assertThat(result.items().getFirst().requiredQty()).isEqualTo(4);
        assertThat(result.totalPackagePrice()).isEqualByComparingTo("190");
    }

    @Test
    void retailPriceDropDoesNotReturnNegativeSavingsOrPurchasable() {
        var variant = new ProductVariant();
        variant.setVariantId(sku);
        variant.setProductId(parent);
        variant.setStatus(VariantStatus.ACTIVE);
        variant.setSellingPrice(BigDecimal.ONE);
        when(variants.findAllById(any())).thenReturn(List.of(variant));
        when(inventory.availableQuantities(any())).thenReturn(Map.of(sku, BigDecimal.TEN));
        var result = at(from).evaluate(id, new BundleEvaluationRequest(1, null, List.of()));
        assertThat(result.availability()).isEqualTo("IN_STOCK");
        assertThat(result.priceIssue()).isEqualTo("PACKAGE_EXCEEDS_RETAIL");
        assertThat(result.savingAmount()).isNull();
        assertThat(result.purchasable()).isFalse();
    }

    @Test
    void draftVisibilityDoesNotDependOnInventory() {
        config.setPublicationStatus(BundleConfiguration.PublicationStatus.DRAFT);
        assertThatThrownBy(
                        () ->
                                at(from).evaluate(
                                                id,
                                                new BundleEvaluationRequest(1, null, List.of())))
                .isInstanceOf(AppException.class);
        verifyNoInteractions(inventory);
    }
}
