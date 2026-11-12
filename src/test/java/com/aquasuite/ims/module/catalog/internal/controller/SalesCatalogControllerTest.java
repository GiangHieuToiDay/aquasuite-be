package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.enums.*;
import com.aquasuite.ims.module.catalog.internal.payload.res.BundleResponse;
import com.aquasuite.ims.module.catalog.internal.repository.*;
import com.aquasuite.ims.module.catalog.internal.service.BundleQueryService;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;
import com.aquasuite.ims.share.dto.PageResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SalesCatalogControllerTest {
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final InventoryApi inventory = mock(InventoryApi.class);
    private final BundleQueryService bundles = mock(BundleQueryService.class);
    private final SalesCatalogController controller = new SalesCatalogController(variants, products,
            mock(CategoryRepository.class), mock(BrandRepository.class), mock(ProductImageRepository.class),
            inventory, bundles);

    @Test
    void includesComboWithoutVariantAndUsesProductIdAcrossAllPages() {
        UUID bundleId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        BundleResponse bundle = mock(BundleResponse.class);
        when(bundle.bundleId()).thenReturn(bundleId);
        when(bundle.name()).thenReturn("My combo");
        when(bundle.packagePrice()).thenReturn(BigDecimal.valueOf(900));
        when(bundle.items()).thenReturn(List.of(new BundleResponse.Item(UUID.randomUUID(), componentId,
                "SKU", "Component", 2, BigDecimal.TEN, true)));
        when(bundles.getBundleList(any())).thenAnswer(call -> {
            var request = (com.aquasuite.ims.module.catalog.internal.payload.req.BundlePageRequest) call.getArgument(0);
            assertThat(request.getProductStatus()).isEqualTo(ProductStatus.ACTIVE);
            return PageResponse.of(List.of(bundle), request.getPage(), 100, 101, 2);
        });
        when(inventory.getStockSummaries(any())).thenReturn(Map.of(componentId,
                StockSummaryDto.builder().availableQty(BigDecimal.valueOf(7)).build()));

        var result = controller.getSalesCatalogItems().getBody().getData();

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().isCombo()).isTrue();
        assertThat(result.getFirst().variantId()).isEqualTo(bundleId);
        assertThat(result.getFirst().sellingPrice()).isEqualByComparingTo("900");
        assertThat(result.getFirst().availableStock()).isEqualByComparingTo("3");
        verify(bundles, times(2)).getBundleList(any());
    }

    @Test
    void replacesLegacyComboVariantWithOneBundleRowAndPreservesNormalSku() {
        UUID bundleId = UUID.randomUUID();
        UUID normalId = UUID.randomUUID();
        ProductVariant legacy = ProductVariant.builder().variantId(UUID.randomUUID())
                .productId(bundleId).status(VariantStatus.ACTIVE).build();
        ProductVariant normal = ProductVariant.builder().variantId(UUID.randomUUID())
                .productId(normalId).status(VariantStatus.ACTIVE).build();
        when(variants.findAll()).thenReturn(List.of(legacy, normal));
        when(products.findAllById(any())).thenReturn(List.of(
                Product.builder().productId(bundleId).productType(ProductType.BUNDLE).build(),
                Product.builder().productId(normalId).productType(ProductType.NORMAL).build()));
        BundleResponse bundle = mock(BundleResponse.class);
        when(bundle.bundleId()).thenReturn(bundleId);
        when(bundle.items()).thenReturn(List.of());
        when(bundles.getBundleList(any())).thenReturn(PageResponse.of(List.of(bundle), 0, 100, 1, 1));

        var result = controller.getSalesCatalogItems().getBody().getData();

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().variantId()).isEqualTo(normal.getVariantId());
        assertThat(result.getLast().variantId()).isEqualTo(bundleId);
        assertThat(result.getLast().availableStock()).isEqualByComparingTo("0");
    }
}
