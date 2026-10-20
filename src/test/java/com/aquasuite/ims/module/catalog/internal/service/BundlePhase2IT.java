package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.support.BundleCoreTestSupport;
import com.aquasuite.ims.module.catalog.internal.payload.*;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BundlePhase2IT extends BundleCoreTestSupport {
    static final UUID OPTION = UUID.fromString("20000000-0000-4000-8000-000000000002");
    static final UUID GROUP = UUID.fromString("30000000-0000-4000-8000-000000000001");
    @Autowired BundleStorefrontService storefront;

    BundleCoreCreateRequest definition(String code, Instant from, Instant until, List<BundleComponentGroupRequest> groups) {
        var base = request(code);
        return new BundleCoreCreateRequest(base.productCode(), base.name(), base.categoryId(), base.brandId(),
                base.description(), base.items(), base.packagePrice(), from, until, groups);
    }
    List<BundleComponentGroupRequest> groups() {
        return List.of(new BundleComponentGroupRequest(GROUP, SKU, List.of(SKU, OPTION)));
    }
    void option() {
        jdbc.update("INSERT INTO product_variants(variant_id,product_id,sku,variant_name,unit,conversion_rate,weight,cost_price,selling_price,serial_tracking,status) VALUES(?,?,?,?,'piece',1,0,0,110000000,false,'ACTIVE')",
                OPTION, NORMAL, "SKU-2", "Option");
    }
    BundleCoreDetail published() {
        option();
        var now = Instant.now();
        var d = service.create(definition("PUBLIC", now.minusSeconds(60), now.plusSeconds(3600), groups()));
        return service.publish(d.bundleId(), new BundleVersionRequest(d.version()));
    }
    void stock(UUID variant, int physical, int reserved) {
        jdbc.update("INSERT INTO inventories(inventory_id,variant_id,warehouse_code,on_hand_qty,reserved_qty,minimum_qty,updated_at) VALUES(?,?,'WH_MAIN',?,?,0,CURRENT_TIMESTAMP)",
                UUID.randomUUID(), variant, physical, reserved);
    }
    BundleEvaluationRequest evaluate(int quantity, UUID option) {
        return new BundleEvaluationRequest(quantity, null,
                option == null ? List.of() : List.of(new BundleEvaluationRequest.Selection(GROUP, option)));
    }

    @Test void publishAndUnpublishRespectVersionsAndCurrentRetryNoop() {
        var d = published();
        assertThat(d.publicationStatus()).isEqualTo("PUBLISHED");
        assertThat(d.version()).isEqualTo(1);
        assertThat(service.publish(d.bundleId(), new BundleVersionRequest(d.version())).version()).isEqualTo(d.version());
        assertThatThrownBy(() -> service.unpublish(d.bundleId(), new BundleVersionRequest(0L))).isInstanceOf(AppException.class);
        var hidden = service.unpublish(d.bundleId(), new BundleVersionRequest(d.version()));
        assertThat(hidden.version()).isEqualTo(2);
        assertThat(service.unpublish(d.bundleId(), new BundleVersionRequest(hidden.version())).version()).isEqualTo(hidden.version());
        assertThatThrownBy(() -> storefront.get(d.bundleId())).isInstanceOf(AppException.class);
    }

    @Test void oldDraftNeedsDatesAndInactiveOrExpiredCannotPublish() {
        var d = create();
        assertThatThrownBy(() -> service.publish(d.bundleId(), new BundleVersionRequest(d.version()))).isInstanceOf(AppException.class);
        var now = Instant.now();
        var expired = service.create(definition("EXPIRED", now.minusSeconds(120), now.minusSeconds(60), List.of()));
        assertThatThrownBy(() -> service.publish(expired.bundleId(), new BundleVersionRequest(expired.version()))).isInstanceOf(AppException.class);
        var active = service.create(definition("INACTIVE", now.minusSeconds(60), now.plusSeconds(600), List.of()));
        jdbc.update("UPDATE products SET status='INACTIVE' WHERE product_id=?", active.bundleId());
        assertThatThrownBy(() -> service.publish(active.bundleId(), new BundleVersionRequest(active.version()))).isInstanceOf(AppException.class);
    }

    @Test void publicVisibilityIsAppliedBeforePaginationAndFuturePublishIsHidden() throws Exception {
        var d = published();
        create();
        var now = Instant.now();
        var future = service.create(definition("FUTURE", now.plusSeconds(600), now.plusSeconds(1200), List.of()));
        service.publish(future.bundleId(), new BundleVersionRequest(future.version()));
        var page = storefront.list(0, 1, null);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items().getFirst().bundleId()).isEqualTo(d.bundleId());
        mvc.perform(get("/api/v1/catalog/bundles/" + future.bundleId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/catalog/bundles/" + d.bundleId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.componentGroups[0].groupId").value(GROUP.toString()))
                .andExpect(jsonPath("$.data.costPrice").doesNotExist());
        mvc.perform(get("/api/v1/catalog/bundles?page=0&page=1")).andExpect(status().isBadRequest());
    }

    @Test void samePriceSelectionReplacesDefaultMultipliesQuantitiesAndDoesNotReserve() throws Exception {
        var d = published();
        stock(OPTION, 7, 1);
        var preview = storefront.evaluate(d.bundleId(), evaluate(2, OPTION));
        assertThat(preview.purchasable()).isTrue();
        assertThat(preview.availability()).isEqualTo("IN_STOCK");
        assertThat(preview.items()).hasSize(1);
        assertThat(preview.items().getFirst().variantId()).isEqualTo(OPTION);
        assertThat(preview.items().getFirst().requiredQty()).isEqualTo(2);
        assertThat(preview.totalPackagePrice()).isEqualByComparingTo("190000000");
        assertThat(preview.savingAmount()).isEqualByComparingTo("30000000");
        assertThat(jdbc.queryForObject("SELECT reserved_qty FROM inventories WHERE variant_id=?", BigDecimal.class, OPTION)).isEqualByComparingTo("1");
        mvc.perform(post("/api/v1/catalog/bundles/" + d.bundleId() + "/evaluations")
                .contentType("application/json").content(json.writeValueAsString(evaluate(2, OPTION))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.purchasable").value(true));
    }

    @Test void missingStockIsUnknownNegativeStockInsufficientAndDefaultIsUsed() {
        var d = published();
        var missing = storefront.evaluate(d.bundleId(), evaluate(1, null));
        assertThat(missing.availability()).isEqualTo("UNKNOWN");
        assertThat(missing.purchasable()).isFalse();
        stock(SKU, 0, 1);
        var insufficient = storefront.evaluate(d.bundleId(), evaluate(1, null));
        assertThat(insufficient.availability()).isEqualTo("INSUFFICIENT");
        assertThat(insufficient.missingVariantIds()).containsExactly(SKU);
    }

    @Test void whitelistDuplicateGroupStaleAndInvalidQuantityAreRejected() throws Exception {
        var d = published();
        assertThatThrownBy(() -> storefront.evaluate(d.bundleId(), evaluate(1, UUID.randomUUID()))).isInstanceOf(AppException.class);
        var selected = new BundleEvaluationRequest.Selection(GROUP, OPTION);
        assertThatThrownBy(() -> storefront.evaluate(d.bundleId(), new BundleEvaluationRequest(1, null, List.of(selected, selected)))).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> storefront.evaluate(d.bundleId(), new BundleEvaluationRequest(1, 0L, List.of()))).isInstanceOf(AppException.class);
        mvc.perform(post("/api/v1/catalog/bundles/" + d.bundleId() + "/evaluations").contentType("application/json")
                .content("{\"quantity\":1.5}")).andExpect(status().isBadRequest());
        assertThatThrownBy(() -> storefront.evaluate(d.bundleId(), evaluate(0, null))).isInstanceOf(AppException.class);
    }

    @Test void unequalOptionOrDuplicateDefaultIsRejectedWithoutPartialCreate() {
        option();
        var now = Instant.now();
        jdbc.update("UPDATE product_variants SET selling_price=120000000 WHERE variant_id=?", OPTION);
        assertThatThrownBy(() -> service.create(definition("BAD", now, now.plusSeconds(600), groups()))).isInstanceOf(AppException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE product_code='BAD'", Integer.class)).isZero();
        jdbc.update("UPDATE product_variants SET selling_price=110000000 WHERE variant_id=?", OPTION);
        var duplicate = new BundleComponentGroupRequest(UUID.randomUUID(), SKU, List.of(SKU));
        assertThatThrownBy(() -> service.create(definition("BAD2", now, now.plusSeconds(600), List.of(groups().getFirst(), duplicate)))).isInstanceOf(AppException.class);
    }

    @Test void currentPricesAreRevalidatedAtPublishAndEvaluation() {
        var d = published();
        jdbc.update("UPDATE product_variants SET selling_price=120000000 WHERE variant_id=?", OPTION);
        assertThatThrownBy(() -> storefront.evaluate(d.bundleId(), evaluate(1, OPTION))).isInstanceOf(AppException.class);
        var unpublished = service.unpublish(d.bundleId(), new BundleVersionRequest(d.version()));
        assertThatThrownBy(() -> service.publish(d.bundleId(), new BundleVersionRequest(unpublished.version()))).isInstanceOf(AppException.class);
    }

    @Test void updatingGroupsAndDatesUsesTheExistingAggregateVersionAndRollsBackInvalidChanges() {
        var d = published();
        var hidden = service.unpublish(d.bundleId(), new BundleVersionRequest(d.version()));
        var changed = new BundleCoreUpdateRequest(hidden.productCode(), "Changed", CATEGORY, null, null,
                List.of(new BundleItemRequest(SKU, 2)), hidden.packagePrice(), hidden.version(),
                hidden.validFrom(), hidden.validUntil(), groups());
        var updated = service.update(d.bundleId(), changed);
        assertThat(updated.componentGroups()).hasSize(1);
        assertThat(updated.items().getFirst().quantity()).isEqualTo(2);
        assertThat(updated.version()).isEqualTo(hidden.version() + 1);
        assertThatThrownBy(() -> service.update(d.bundleId(), changed)).isInstanceOf(AppException.class);
        var invalid = new BundleCoreUpdateRequest(hidden.productCode(), "Bad", CATEGORY, null, null,
                List.of(new BundleItemRequest(SKU, 3)), hidden.packagePrice(), updated.version(),
                hidden.validUntil(), hidden.validFrom(), groups());
        assertThatThrownBy(() -> service.update(d.bundleId(), invalid)).isInstanceOf(AppException.class);
        assertThat(service.get(d.bundleId()).name()).isEqualTo("Changed");
        assertThat(service.get(d.bundleId()).items().getFirst().quantity()).isEqualTo(2);
    }
    @Test void finalDuplicateSkuAndInactiveReplacementAreRejected() {
        option();
        var now = Instant.now();
        var d = service.create(new BundleCoreCreateRequest("DUPLICATE", "Bundle", CATEGORY, null, null,
                List.of(new BundleItemRequest(SKU, 1), new BundleItemRequest(OPTION, 1)),
                new BigDecimal("95000000"), now.minusSeconds(60), now.plusSeconds(600), groups()));
        var active = service.publish(d.bundleId(), new BundleVersionRequest(d.version()));
        assertThatThrownBy(() -> storefront.evaluate(active.bundleId(), evaluate(1, OPTION))).isInstanceOf(AppException.class);
        var single = service.create(definition("SINGLE", now.minusSeconds(60), now.plusSeconds(600), groups()));
        single = service.publish(single.bundleId(), new BundleVersionRequest(single.version()));
        UUID id = single.bundleId();
        jdbc.update("UPDATE product_variants SET status='INACTIVE' WHERE variant_id=?", OPTION);
        assertThatThrownBy(() -> storefront.evaluate(id, evaluate(1, OPTION))).isInstanceOf(AppException.class);
    }

    @Test void groupWriteFailureRollsBackProductConfigurationAndItems() {
        option();
        var now = Instant.now();
        jdbc.execute("ALTER TABLE bundle_component_groups ADD CONSTRAINT reject_test_group CHECK (false)");
        try {
            assertThatThrownBy(() -> service.create(definition("ROLLBACK", now, now.plusSeconds(600), groups())))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE product_code='ROLLBACK'", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM bundle_configurations", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM bundle_items", Integer.class)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE bundle_component_groups DROP CONSTRAINT reject_test_group");
        }
    }

    @Test void publishHttpRejectsStaleAndUnpublishMakesPublicIdNotFound() throws Exception {
        var now = Instant.now();
        var d = service.create(definition("HTTP", now.minusSeconds(60), now.plusSeconds(600), List.of()));
        String url = "/api/v1/catalog/admin/bundles/" + d.bundleId();
        var result = mvc.perform(post(url + "/publish").contentType("application/json")
                .content(json.writeValueAsString(new BundleVersionRequest(d.version()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("MSG44")).andReturn();
        long version = json.readTree(result.getResponse().getContentAsByteArray()).at("/data/version").asLong();
        mvc.perform(post(url + "/publish").contentType("application/json")
                .content(json.writeValueAsString(new BundleVersionRequest(d.version()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATALOG_BUNDLE_003"));
        mvc.perform(post(url + "/unpublish").contentType("application/json")
                .content(json.writeValueAsString(new BundleVersionRequest(version)))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/catalog/bundles/" + d.bundleId())).andExpect(status().isNotFound());
    }

    @Test void actualInventoryQueryFailureDoesNotPoisonCatalogTransaction() {
        var d = published();
        jdbc.execute("ALTER TABLE inventories RENAME TO temporarily_unavailable_inventories");
        try {
            var result = storefront.evaluate(d.bundleId(), evaluate(1, null));
            assertThat(result.availability()).isEqualTo("UNKNOWN");
            assertThat(result.purchasable()).isFalse();
            assertThat(service.get(d.bundleId()).version()).isEqualTo(d.version());
        } finally {
            jdbc.execute("ALTER TABLE temporarily_unavailable_inventories RENAME TO inventories");
        }
    }

    @Test void publishAndUpdateCompetingAtSameVersionOnlyCommitOneMutation() throws Exception {
        var now = Instant.now();
        var d = service.create(definition("RACE", now.minusSeconds(60), now.plusSeconds(600), List.of()));
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                start.await();
                try { service.publish(d.bundleId(), new BundleVersionRequest(d.version())); return true; }
                catch (AppException conflict) { return false; }
            });
            var second = pool.submit(() -> {
                start.await();
                try { service.update(d.bundleId(), update(d)); return true; }
                catch (AppException conflict) { return false; }
            });
            start.countDown();
            assertThat(List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(service.get(d.bundleId()).version()).isEqualTo(1);
        }
    }

}
