package com.aquasuite.ims.controller.catalog;

import com.aquasuite.ims.entity.catalog.*;
import com.aquasuite.ims.enums.catalog.*;
import com.aquasuite.ims.dto.catalog.response.BundleResponse;
import com.aquasuite.ims.repository.catalog.*;
import com.aquasuite.ims.service.catalog.BundleService;
import com.aquasuite.ims.service.inventory.InventoryService;
import com.aquasuite.ims.dto.inventory.response.StockSummaryDto;
import com.aquasuite.ims.common.PageResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SalesCatalogControllerTest {
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final BundleService bundles = mock(BundleService.class);
    private final ProductController controller = new ProductController(null, null, null, null, null, variants, products,
            mock(CategoryRepository.class), mock(BrandRepository.class), mock(ProductImageRepository.class),
            inventory, bundles);

    @Test
    void includesComboWithoutVariantAndUsesProductIdAcrossAllPages() {
        UUID bundleId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        BundleResponse bundle = bundle(bundleId, "My combo", BigDecimal.valueOf(900),
                List.of(new BundleResponse.Item(UUID.randomUUID(), componentId,
                        "SKU", "Component", 2, BigDecimal.TEN, true)));
        when(bundles.getBundleList(any())).thenAnswer(call -> {
            var request = (com.aquasuite.ims.dto.catalog.request.BundlePageRequest) call.getArgument(0);
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
        BundleResponse bundle = bundle(bundleId, null, null, List.of());
        when(bundles.getBundleList(any())).thenReturn(PageResponse.of(List.of(bundle), 0, 100, 1, 1));

        var result = controller.getSalesCatalogItems().getBody().getData();

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().variantId()).isEqualTo(normal.getVariantId());
        assertThat(result.getLast().variantId()).isEqualTo(bundleId);
        assertThat(result.getLast().availableStock()).isEqualByComparingTo("0");
    }

    /** BundleResponse là record (final) nên dựng đối tượng thật thay vì mock. */
    private static BundleResponse bundle(UUID id, String name, BigDecimal packagePrice, List<BundleResponse.Item> items) {
        return new BundleResponse(id, null, name, null, null, null, 0, null, packagePrice, null, null, null,
                null, null, null, null, null, items, null, null);
    }
}
