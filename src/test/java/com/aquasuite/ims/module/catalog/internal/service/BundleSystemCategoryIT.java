package com.aquasuite.ims.module.catalog.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aquasuite.ims.module.catalog.internal.payload.req.*;
import com.aquasuite.ims.support.BundleCoreTestSupport;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

class BundleSystemCategoryIT extends BundleCoreTestSupport {
    private UUID systemCategory() {
        return jdbc.queryForObject("select category_id from categories where category_code='709'", UUID.class);
    }

    @Test
    void httpCreateAndUpdateNeedNoClassification() throws Exception {
        var body = (ObjectNode) json.valueToTree(request("NO-CLASSIFICATION"));
        body.remove(List.of("categoryId", "brandId"));
        var result = mvc.perform(post("/api/v1/catalog/bundle/admin")
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated()).andReturn();
        var detail = json.readTree(result.getResponse().getContentAsByteArray()).get("data");
        assertThat(detail.get("categoryId").asText()).isEqualTo(systemCategory().toString());
        assertThat(jdbc.queryForObject("select brand_id from products where product_code='NO-CLASSIFICATION'", UUID.class)).isNull();
        body.put("expectedVersion", detail.get("version").asLong());
        body.put("name", "Updated");
        mvc.perform(put("/api/v1/catalog/bundle/admin/" + detail.get("bundleId").asText())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryId").value(systemCategory().toString()));
    }

    @Test
    void forgedLegacyIdsCannotChooseCategoryOrBrand() {
        var r = request("FORGED");
        var d = service.createBundle(new CreateBundleRequest(r.productCode(), r.name(),
                UUID.randomUUID(), UUID.randomUUID(), r.description(), r.items(), r.packagePrice()));
        assertThat(d.categoryId()).isEqualTo(systemCategory());
        assertThat(d.brandId()).isNull();
        var updated = service.updateBundle(d.bundleId(), new UpdateBundleRequest(d.productCode(), "Updated",
                UUID.randomUUID(), UUID.randomUUID(), null, r.items(), r.packagePrice(), d.version()));
        assertThat(updated.categoryId()).isEqualTo(d.categoryId());
        assertThat(updated.brandId()).isNull();
    }

    @Test
    void updatePreservesHistoricalCategoryAndBrandAndPublicationDoesNotRequireThemActive() {
        var d = create();
        UUID brand = UUID.randomUUID();
        jdbc.update("insert into brands(brand_id,brand_code,brand_name,status) values(?,'LEGACY','Legacy','INACTIVE')", brand);
        jdbc.update("update products set category_id=?,brand_id=? where product_id=?", CATEGORY, brand, d.bundleId());
        jdbc.update("update categories set status='INACTIVE' where category_id=?", CATEGORY);
        var r = request("unused");
        var now = Instant.now();
        var updated = service.updateBundle(d.bundleId(), new UpdateBundleRequest(d.productCode(), "Updated",
                null, null, null, r.items(), r.packagePrice(), d.version(), now.minusSeconds(60), now.plusSeconds(3600)));
        assertThat(updated.categoryId()).isEqualTo(CATEGORY);
        assertThat(updated.brandId()).isEqualTo(brand);
        assertThat(service.changePublicationStatus(d.bundleId(),
                new ChangePublicationStatusRequest(ChangePublicationStatusRequest.Status.PUBLISHED, updated.version()))
                .publicationStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void missingSeedIsNotCreatedByBundleRequests() {
        jdbc.update("delete from categories where category_code='709'");
        assertThatThrownBy(() -> service.createBundle(request("MISSING-SEED")))
                .isInstanceOf(com.aquasuite.ims.share.exception.AppException.class);
        assertThat(jdbc.queryForObject("select count(*) from categories where category_code='709'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from products where product_code='MISSING-SEED'", Integer.class))
                .isZero();
    }

    @Test
    void concurrentRequestsReuseSeededCategory() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from categories where category_code='709'", Integer.class)).isEqualTo(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var tasks = new ArrayList<Future<UUID>>();
            for (int i = 0; i < 2; i++) {
                String code = "CONCURRENT-" + i;
                tasks.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                    return service.createBundle(request(code)).categoryId();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var first = tasks.getFirst().get(20, TimeUnit.SECONDS);
            assertThat(tasks.get(1).get(20, TimeUnit.SECONDS)).isEqualTo(first);
        }
        assertThat(jdbc.queryForObject("select count(*) from categories where category_code='709'", Integer.class)).isEqualTo(1);
    }

    @Test
    void comboCategoryUsesOrdinaryCategoryRulesAndProductConstraintsAreUnchanged() {
        var d = create();
        jdbc.update("update categories set category_name='Combo updated',parent_id=?,status='INACTIVE',category_code='OTHER' where category_id=?",
                CATEGORY, d.categoryId());
        assertThat(jdbc.queryForObject("select category_code from categories where category_id=?", String.class, d.categoryId()))
                .isEqualTo("OTHER");
        assertThat(jdbc.queryForObject("select count(*) from pg_trigger where tgname='trg_protect_system_combo_category'", Integer.class))
                .isZero();
        // Only the ordinary product foreign key prevents deletion while referenced.
        assertThatThrownBy(() -> jdbc.update("delete from categories where category_id=?", d.categoryId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update products set category_id=null where product_id=?", d.bundleId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update products set category_id=null where product_id=?", NORMAL))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("update products set brand_id=null where product_id=?", d.bundleId());
        assertThat(jdbc.queryForObject("select category_id from products where product_id=?", UUID.class, d.bundleId()))
                .isEqualTo(d.categoryId());
    }

    @Test
    void comboCategoryCanBeAChildLikeOrdinaryCategories() {
        var first = create();
        jdbc.update("update categories set parent_id=? where category_id=?", CATEGORY, first.categoryId());
        assertThat(service.createBundle(request("CHILD-CATEGORY")).categoryId()).isEqualTo(first.categoryId());
    }

}
