package com.aquasuite.ims.module.catalog.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.entity.enums.*;
import com.aquasuite.ims.module.catalog.internal.mapper.BundleCoreMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class BundleDetailAssemblerTest {
    private final BundleCoreMapper mapper = Mappers.getMapper(BundleCoreMapper.class);
    private final BundleDetailAssembler assembler =
            new BundleDetailAssembler(new BundlePricingService(), mapper);
    private Product bundle;
    private Product parent;
    private ProductVariant variant;
    private BundleItem item;
    private BundleConfiguration configuration;

    @BeforeEach
    void setup() {
        bundle = Product.builder()
                .productId(UUID.randomUUID()).productCode("COMBO-TEST").productName("Combo")
                .productType(ProductType.BUNDLE).status(ProductStatus.ACTIVE)
                .categoryId(UUID.randomUUID()).brandId(UUID.randomUUID()).description("Description")
                .build();
        parent = Product.builder()
                .productId(UUID.randomUUID()).productName("Normal product")
                .productType(ProductType.NORMAL).status(ProductStatus.ACTIVE).build();
        variant = ProductVariant.builder()
                .variantId(UUID.randomUUID()).productId(parent.getProductId())
                .sku("SKU-TEST").variantName("Variant").unit("piece")
                .sellingPrice(new BigDecimal("110000000.00")).status(VariantStatus.ACTIVE).build();
        item = BundleItem.builder()
                .bundleItemId(UUID.randomUUID()).bundleProductId(bundle.getProductId())
                .variantId(variant.getVariantId()).quantity(1).build();
        configuration = new BundleConfiguration();
        configuration.setBundleProductId(bundle.getProductId());
        configuration.setPackagePrice(new BigDecimal("95000000.00"));
        configuration.setVersion(3L);
        configuration.setCreatedAt(Instant.parse("2026-10-17T00:00:00Z"));
        configuration.setUpdatedAt(Instant.parse("2026-10-19T00:00:00Z"));
    }

    @Test
    void generatedMapperPreservesResponseFieldsAndPricing() {
        var detail = assembler.detail(bundle, configuration, List.of(item),
                Map.of(variant.getVariantId(), variant), Map.of(parent.getProductId(), parent));
        assertThat(detail.bundleId()).isEqualTo(bundle.getProductId());
        assertThat(detail.productCode()).isEqualTo(bundle.getProductCode());
        assertThat(detail.name()).isEqualTo(bundle.getProductName());
        assertThat(detail.productStatus()).isEqualTo("ACTIVE");
        assertThat(detail.publicationStatus()).isEqualTo("DRAFT");
        assertThat(detail.version()).isEqualTo(3L);
        assertThat(detail.createdAt()).isEqualTo(configuration.getCreatedAt());
        assertThat(detail.updatedAt()).isEqualTo(configuration.getUpdatedAt());
        assertThat(detail.categoryId()).isEqualTo(bundle.getCategoryId());
        assertThat(detail.brandId()).isEqualTo(bundle.getBrandId());
        assertThat(detail.description()).isEqualTo(bundle.getDescription());
        assertThat(detail.linkedItemsCount()).isEqualTo(1);
        assertThat(detail.originalTotal()).isEqualByComparingTo("110000000.00");
        assertThat(detail.packagePrice()).isEqualByComparingTo("95000000.00");
        assertThat(detail.savingAmount()).isEqualByComparingTo("15000000.00");
        assertThat(detail.discountPercent()).isEqualByComparingTo("13.6");
        assertThat(detail.priceIssue()).isNull();
        var line = detail.items().getFirst();
        assertThat(line.bundleItemId()).isEqualTo(item.getBundleItemId());
        assertThat(line.variantId()).isEqualTo(variant.getVariantId());
        assertThat(line.sku()).isEqualTo("SKU-TEST");
        assertThat(line.name()).isEqualTo("Variant");
        assertThat(line.quantity()).isEqualTo(1);
        assertThat(line.sellingPrice()).isEqualByComparingTo("110000000.00");
        assertThat(line.sellable()).isTrue();
        assertThat(mapper.summary(detail)).usingRecursiveComparison().isEqualTo(detail);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "inactiveVariant", "inactiveParent", "deletedParent", "bundleParent", "missingParent"
    })
    void unavailableComponentIsRetainedAndMarkedUnsellable(String state) {
        switch (state) {
            case "inactiveVariant" -> variant.setStatus(VariantStatus.INACTIVE);
            case "inactiveParent" -> parent.setStatus(ProductStatus.INACTIVE);
            case "deletedParent" -> parent.setStatus(ProductStatus.DELETED);
            case "bundleParent" -> parent.setProductType(ProductType.BUNDLE);
            case "missingParent" -> { }
            default -> throw new AssertionError(state);
        }
        Map<UUID, Product> parents = state.equals("missingParent")
                ? Map.of() : Map.of(parent.getProductId(), parent);
        var detail = assembler.detail(bundle, configuration, List.of(item),
                Map.of(variant.getVariantId(), variant), parents);
        assertThat(detail.items()).hasSize(1);
        assertThat(detail.items().getFirst().sellable()).isFalse();
        assertThat(detail.items().getFirst().variantId()).isEqualTo(item.getVariantId());
        assertThat(detail.originalTotal()).isEqualByComparingTo("110000000.00");
    }

    @Test
    void missingVariantKeepsReferenceAndReportsPriceIssue() {
        var detail = assembler.detail(bundle, configuration, List.of(item), Map.of(), Map.of());
        var line = detail.items().getFirst();
        assertThat(line.variantId()).isEqualTo(item.getVariantId());
        assertThat(line.bundleItemId()).isEqualTo(item.getBundleItemId());
        assertThat(line.sku()).isNull();
        assertThat(line.name()).isNull();
        assertThat(line.sellingPrice()).isNull();
        assertThat(line.sellable()).isFalse();
        assertThat(detail.priceIssue()).isEqualTo("INVALID_RETAIL_TOTAL");
        assertThat(detail.originalTotal()).isNull();
    }

    @Test
    void currentRetailPriceDropDoesNotProduceNegativeDiscount() {
        variant.setSellingPrice(BigDecimal.ONE);
        var detail = assembler.detail(bundle, configuration, List.of(item),
                Map.of(variant.getVariantId(), variant), Map.of(parent.getProductId(), parent));
        assertThat(detail.originalTotal()).isEqualByComparingTo("1");
        assertThat(detail.priceIssue()).isEqualTo("PACKAGE_EXCEEDS_RETAIL");
        assertThat(detail.savingAmount()).isNull();
        assertThat(detail.discountPercent()).isNull();
    }

    @Test
    void generatedMutationAndLookupUseTheirOwnContracts() {
        bundle.setStatus(ProductStatus.INACTIVE);
        var mutation = mapper.mutation(bundle, configuration);
        assertThat(mutation.bundleId()).isEqualTo(bundle.getProductId());
        assertThat(mutation.productStatus()).isEqualTo("INACTIVE");
        assertThat(mutation.publicationStatus()).isEqualTo("DRAFT");
        assertThat(mutation.version()).isEqualTo(3L);
        var sku = mapper.sku(variant, parent);
        assertThat(sku.variantId()).isEqualTo(variant.getVariantId());
        assertThat(sku.productId()).isEqualTo(parent.getProductId());
        assertThat(sku.productName()).isEqualTo("Normal product");
        assertThat(sku.variantName()).isEqualTo("Variant");
        assertThat(sku.sku()).isEqualTo("SKU-TEST");
        assertThat(sku.unit()).isEqualTo("piece");
        assertThat(sku.sellingPrice()).isEqualByComparingTo("110000000.00");
    }
}
