package com.aquasuite.ims.module.catalog.internal.service.excel;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.aquasuite.ims.module.catalog.internal.mapper.ProductExcelMapper;
import com.aquasuite.ims.module.catalog.internal.payload.excel.*;
import com.aquasuite.ims.module.catalog.internal.repository.*;
import com.aquasuite.ims.share.excel.dto.ExcelRowError;
import com.aquasuite.ims.share.excel.storage.TempExcelFileManager;
import com.aquasuite.ims.share.excel.tracker.RedisImportProgressTracker;
import com.aquasuite.ims.support.BundleCoreTestSupport;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.*;

class BundleImportGuardIT extends BundleCoreTestSupport {
    @Autowired CategoryRepository categories;
    @Autowired BrandRepository brands;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired ProductImageRepository images;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;

    private ProductBulkExcelImporter importer() {
        var i =
                new ProductBulkExcelImporter(
                        mock(RedisImportProgressTracker.class),
                        mock(TempExcelFileManager.class),
                        manager,
                        categories,
                        brands,
                        products,
                        variants,
                        images,
                        new ProductExcelMapper(),
                        mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(i, "entityManager", em);
        return i;
    }

    private ProductExcelRowDto row(String code, String sku) {
        return ProductExcelRowDto.builder()
                .rowNumber(3)
                .productCode(code)
                .productName("Imported")
                .categoryCode("CAT")
                .sku(sku)
                .variantName("Variant")
                .unit("piece")
                .sellingPrice(BigDecimal.TEN)
                .costPrice(BigDecimal.ZERO)
                .imageUrls(List.of())
                .build();
    }

    @ParameterizedTest
    @EnumSource(ImportMode.class)
    void comboCodeCannotBeOverwritten(ImportMode mode) {
        var d = create();
        var errors = new ArrayList<ExcelRowError>();
        Integer n =
                tx.execute(
                        t ->
                                importer()
                                        .processAndPersistBatch(
                                                List.of(row(d.productCode(), "NEW")),
                                                new ProductImportLookupContext(
                                                        mode, Map.of("CAT", CATEGORY), Map.of()),
                                                errors));
        assertThat(n).isZero();
        assertThat(errors).hasSize(1);
        assertThat(query.getBundleDetails(d.bundleId()).name()).isEqualTo(d.name());
        assertThat(jdbc.queryForObject("select count(*) from product_variants", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void skuBelongingToBundleCannotBeMoved() {
        var d = create();
        jdbc.update(
                "update product_variants set product_id=? where variant_id=?", d.bundleId(), SKU);
        var errors = new ArrayList<ExcelRowError>();
        tx.executeWithoutResult(
                t ->
                        assertThat(
                                        importer()
                                                .processAndPersistBatch(
                                                        List.of(row("OTHER", "SKU-1")),
                                                        new ProductImportLookupContext(
                                                                ImportMode.OVERWRITE_EXISTING,
                                                                Map.of("CAT", CATEGORY),
                                                                Map.of()),
                                                        errors))
                                .isZero());
        assertThat(errors).hasSize(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from products where product_code='OTHER'",
                                Integer.class))
                .isZero();
    }

    @Test
    void normalImportStillWorks() {
        var errors = new ArrayList<ExcelRowError>();
        Integer n =
                tx.execute(
                        t ->
                                importer()
                                        .processAndPersistBatch(
                                                List.of(row("NORMAL-IMPORT", "NEW-SKU")),
                                                new ProductImportLookupContext(
                                                        ImportMode.SKIP_EXISTING,
                                                        Map.of("CAT", CATEGORY),
                                                        Map.of()),
                                                errors));
        assertThat(n).isEqualTo(1);
        assertThat(errors).isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from product_variants where sku='NEW-SKU'",
                                Integer.class))
                .isEqualTo(1);
    }
}
