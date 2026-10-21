package com.aquasuite.ims.module.catalog.internal.service;

import static org.assertj.core.api.Assertions.*;

import com.aquasuite.ims.module.catalog.internal.payload.req.BundleCoreCreateRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.BundleCoreUpdateRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.BundleItemRequest;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.support.BundleCoreTestSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.*;

class BundleAdminServiceIT extends BundleCoreTestSupport {
    @Test
    void listingMultipleBundlesKeepsEachBundlesOwnItemsAndPrice() {
        var first = create();
        var second =
                service.create(
                        new BundleCoreCreateRequest(
                                "BUNDLE-2",
                                "Second combo",
                                CATEGORY,
                                null,
                                null,
                                List.of(new BundleItemRequest(SKU, 2)),
                                new BigDecimal("95000000.00")));

        var page = service.list(0, 20, null, null, null);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items())
                .filteredOn(b -> b.bundleId().equals(first.bundleId()))
                .singleElement()
                .satisfies(
                        b -> {
                            assertThat(b.linkedItemsCount()).isEqualTo(1);
                            assertThat(b.originalTotal()).isEqualByComparingTo("110000000.00");
                        });
        assertThat(page.items())
                .filteredOn(b -> b.bundleId().equals(second.bundleId()))
                .singleElement()
                .satisfies(
                        b -> {
                            assertThat(b.linkedItemsCount()).isEqualTo(1);
                            assertThat(b.originalTotal()).isEqualByComparingTo("220000000.00");
                        });
    }

    @Test
    void failedReplacementRestoresDefinitionItemsAndVersion() {
        var original = service.get(create().bundleId());
        var replacementSku = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO product_variants(variant_id,product_id,sku,variant_name,unit,"
                        + "conversion_rate,weight,cost_price,selling_price,serial_tracking,status)"
                        + " SELECT ?,product_id,'SKU-2',variant_name,unit,conversion_rate,weight,"
                        + "cost_price,selling_price,serial_tracking,status FROM product_variants"
                        + " WHERE variant_id=?",
                replacementSku,
                SKU);
        jdbc.execute(
                "ALTER TABLE bundle_items ADD CONSTRAINT reject_replacement CHECK(quantity <> 2)");
        try {
            var replacement =
                    new BundleCoreUpdateRequest(
                            original.productCode(),
                            "Must roll back",
                            CATEGORY,
                            null,
                            "Must roll back too",
                            List.of(new BundleItemRequest(replacementSku, 2)),
                            new BigDecimal("90000000.00"),
                            original.version());
            assertThatThrownBy(() -> service.update(original.bundleId(), replacement))
                    .isInstanceOf(RuntimeException.class);
            assertThat(service.get(original.bundleId())).isEqualTo(original);
            assertThat(jdbc.queryForObject("select count(*) from bundle_items", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from audit_entries", Integer.class))
                    .isZero();
        } finally {
            jdbc.execute("ALTER TABLE bundle_items DROP CONSTRAINT reject_replacement");
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missingSku",
                "inactiveSku",
                "inactiveProduct",
                "deletedProduct",
                "nestedBundle",
                "missingCategory",
                "inactiveCategory"
            })
    void invalidReferencesDoNotCreatePartialBundle(String scenario) {
        UUID variant = SKU;
        UUID category = CATEGORY;
        switch (scenario) {
            case "missingSku" -> variant = UUID.randomUUID();
            case "inactiveSku" ->
                    jdbc.update(
                            "update product_variants set status='INACTIVE' where variant_id=?",
                            SKU);
            case "inactiveProduct" ->
                    jdbc.update("update products set status='INACTIVE' where product_id=?", NORMAL);
            case "deletedProduct" ->
                    jdbc.update("update products set status='DELETED' where product_id=?", NORMAL);
            case "nestedBundle" ->
                    jdbc.update(
                            "update products set product_type='BUNDLE' where product_id=?", NORMAL);
            case "missingCategory" -> category = UUID.randomUUID();
            case "inactiveCategory" ->
                    jdbc.update(
                            "update categories set status='INACTIVE' where category_id=?",
                            CATEGORY);
            default -> throw new AssertionError(scenario);
        }
        var request =
                new BundleCoreCreateRequest(
                        "INVALID-REFERENCE",
                        "Invalid",
                        category,
                        null,
                        null,
                        List.of(new BundleItemRequest(variant, 1)),
                        new BigDecimal("95000000.00"));
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(
                        AppException.class,
                        ex ->
                                assertThat(ex.getErrorCode().getCode())
                                        .isEqualTo(
                                                scenario.endsWith("Category")
                                                        ? "CATALOG_BUNDLE_002"
                                                        : "CATALOG_BUNDLE_001"));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from products where product_code='INVALID-REFERENCE'",
                                Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from bundle_configurations", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from bundle_items", Integer.class))
                .isZero();
    }

    @Test
    void publishedDefinitionCannotBeReplaced() {
        var original = create();
        jdbc.update(
                "update bundle_configurations set publication_status='PUBLISHED',"
                        + " valid_from=CURRENT_TIMESTAMP, valid_until=CURRENT_TIMESTAMP + interval '1 day'"
                        + " where bundle_product_id=?",
                original.bundleId());
        var published = service.get(original.bundleId());
        assertThatThrownBy(() -> service.update(original.bundleId(), update(original)))
                .isInstanceOfSatisfying(
                        AppException.class,
                        ex ->
                                assertThat(ex.getErrorCode().getCode())
                                        .isEqualTo("CATALOG_BUNDLE_006"));
        assertThat(service.get(original.bundleId())).isEqualTo(published);
    }

    @Test
    void createAndReplacePreserveItemId() {
        var d = create();
        assertThat(d.productStatus()).isEqualTo("ACTIVE");
        assertThat(d.publicationStatus()).isEqualTo("DRAFT");
        var u = service.update(d.bundleId(), update(d));
        assertThat(u.version()).isEqualTo(d.version() + 1);
        assertThat(u.items().getFirst().bundleItemId())
                .isEqualTo(d.items().getFirst().bundleItemId());
        assertThat(u.items().getFirst().quantity()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from audit_entries", Integer.class))
                .isZero();
    }

    @Test
    void identicalPutStillBumps() {
        var d = create();
        var r =
                new BundleCoreUpdateRequest(
                        d.productCode(),
                        d.name(),
                        CATEGORY,
                        null,
                        null,
                        request(d.productCode()).items(),
                        d.packagePrice(),
                        d.version());
        assertThat(service.update(d.bundleId(), r).version()).isEqualTo(d.version() + 1);
    }

    @Test
    void badReferencesAndDuplicateRollback() {

        var good = request("X");
        var bad =
                new BundleCoreCreateRequest(
                        "X",
                        "Bad",
                        CATEGORY,
                        null,
                        null,
                        List.of(good.items().getFirst(), good.items().getFirst()),
                        good.packagePrice());
        assertThatThrownBy(() -> service.create(bad)).isInstanceOf(AppException.class);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from products where product_type='BUNDLE'",
                                Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_entries", Integer.class))
                .isZero();
    }

    @Test
    void itemConstraintFailureRollsBackAll() {

        jdbc.execute(
                "ALTER TABLE bundle_items ADD CONSTRAINT reject_test_item CHECK(quantity <> 1)");
        try {
            assertThatThrownBy(() -> service.create(request("ROLLBACK")))
                    .isInstanceOf(RuntimeException.class);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from products where product_code='ROLLBACK'",
                                    Integer.class))
                    .isZero();
            assertThat(jdbc.queryForObject("select count(*) from bundle_items", Integer.class))
                    .isZero();
        } finally {
            jdbc.execute("ALTER TABLE bundle_items DROP CONSTRAINT reject_test_item");
        }
    }

    @Test
    void inactiveSkuAndNestingRejected() {
        var d = create();
        jdbc.update("update product_variants set status='INACTIVE' where variant_id=?", SKU);
        assertThatThrownBy(() -> service.update(d.bundleId(), update(d)))
                .isInstanceOf(AppException.class);
        assertThat(service.get(d.bundleId()).items().getFirst().sellable()).isFalse();
    }
}
