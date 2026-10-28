package com.aquasuite.ims.module.catalog.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aquasuite.ims.module.catalog.internal.payload.req.ChangeBundleStatusRequest;
import com.aquasuite.ims.support.BundleCoreTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class BundleCoreHttpIT extends BundleCoreTestSupport {
    @Test
    void sharedItemPayloadKeepsCreateAndUpdateContractsDistinct() throws Exception {
        var detail = create();
        var body =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        json.valueToTree(request(detail.productCode()));
        body.put("expectedVersion", detail.version());
        mvc.perform(
                        post("/api/v1/catalog/bundle/admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));
        body.remove("expectedVersion");
        mvc.perform(
                        put("/api/v1/catalog/bundle/admin/" + detail.bundleId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));
        body.put("expectedVersion", detail.version());
        var item = (com.fasterxml.jackson.databind.node.ObjectNode) body.get("items").get(0);
        item.put("quantity", 0);
        mvc.perform(
                        put("/api/v1/catalog/bundle/admin/" + detail.bundleId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));
        item.put("quantity", 1);
        item.put("unexpected", true);
        mvc.perform(
                        put("/api/v1/catalog/bundle/admin/" + detail.bundleId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));
        assertThat(query.getBundleDetails(detail.bundleId()).version()).isEqualTo(detail.version());
    }

    @Test
    void adminFiltersAndPagingExcludeDeletedAndNormalProducts() throws Exception {
        var active = service.createBundle(request("FILTER-ACTIVE"));
        var inactive = service.createBundle(request("FILTER-INACTIVE"));
        var removed = service.createBundle(request("FILTER-DELETED"));
        service.changeProductStatus(
                inactive.bundleId(),
                new ChangeBundleStatusRequest(
                        inactive.version(), ChangeBundleStatusRequest.Status.INACTIVE));
        service.deleteBundle(removed.bundleId(), removed.version());
        mvc.perform(get("/api/v1/catalog/bundle/admin").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(
                        get("/api/v1/catalog/bundle/admin")
                                .param("keyword", "  filter-active  ")
                                .param("productStatus", "ACTIVE")
                                .param("publicationStatus", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(
                        jsonPath("$.data.items[0].bundleId").value(active.bundleId().toString()));
        mvc.perform(get("/api/v1/catalog/bundle/admin").param("publicationStatus", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/v1/catalog/bundle/admin").param("productStatus", "DELETED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_002"));
    }

    @Test
    void deletedBundleCannotBeUpdatedOrToggledEvenWithOldVersion() throws Exception {
        var d = create();
        service.deleteBundle(d.bundleId(), d.version());
        Long deletedVersion =
                jdbc.queryForObject(
                        "select version from bundle_configurations where bundle_product_id=?",
                        Long.class,
                        d.bundleId());
        for (long version : new long[] {d.version(), deletedVersion}) {
            var body =
                    (com.fasterxml.jackson.databind.node.ObjectNode)
                            json.valueToTree(request(d.productCode()));
            body.put("expectedVersion", version);
            mvc.perform(
                            put("/api/v1/catalog/bundle/admin/" + d.bundleId())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SYS_004"));
            mvc.perform(
                            patch("/api/v1/catalog/bundle/admin/" + d.bundleId() + "/status")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            json.createObjectNode()
                                                    .put("expectedVersion", version)
                                                    .put("productStatus", "ACTIVE")
                                                    .toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SYS_004"));
        }
        mvc.perform(
                        delete("/api/v1/catalog/bundle/admin/" + d.bundleId())
                                .param("expectedVersion", d.version().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_BUNDLE_003"));
        mvc.perform(
                        delete("/api/v1/catalog/bundle/admin/" + d.bundleId())
                                .param("expectedVersion", deletedVersion.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from bundle_items", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void crudWithoutAuthentication() throws Exception {
        var created =
                mvc.perform(
                                post("/api/v1/catalog/bundle/admin")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsBytes(request("HTTP"))))
                        .andExpect(status().isCreated())
                        .andExpect(header().exists("Location"))
                        .andReturn();
        var d = json.readTree(created.getResponse().getContentAsByteArray()).get("data");
        String id = d.get("bundleId").asText();
        long v = d.get("version").asLong();
        mvc.perform(get("/api/v1/catalog/bundle/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].items[0].quantity").value(1))
                .andExpect(jsonPath("$.data.items[0].categoryId").value(CATEGORY.toString()));
        mvc.perform(get("/api/v1/catalog/bundle/admin").param("page", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
        var put = json.valueToTree(request("HTTP"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) put)
                .put("expectedVersion", v)
                .put("name", "Changed");
        var changed =
                mvc.perform(
                                put("/api/v1/catalog/bundle/admin/" + id)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(put.toString()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.items[0].quantity").value(1))
                        .andExpect(jsonPath("$.data.categoryId").value(CATEGORY.toString()))
                        .andReturn();
        v =
                json.readTree(changed.getResponse().getContentAsByteArray())
                        .at("/data/version")
                        .asLong();
        mvc.perform(get("/api/v1/catalog/bundle/admin/" + id))
                .andExpect(jsonPath("$.data.name").value("Changed"))
                .andExpect(jsonPath("$.data.items[0].costPrice").doesNotExist())
                .andExpect(jsonPath("$.data.availability").doesNotExist());
        var off =
                mvc.perform(
                                patch("/api/v1/catalog/bundle/admin/" + id + "/status")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"expectedVersion\":"
                                                        + v
                                                        + ",\"productStatus\":\"INACTIVE\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.productStatus").value("INACTIVE"))
                        .andExpect(jsonPath("$.data.items[0].quantity").value(1))
                        .andExpect(jsonPath("$.data.categoryId").value(CATEGORY.toString()))
                        .andReturn();
        v = json.readTree(off.getResponse().getContentAsByteArray()).at("/data/version").asLong();
        mvc.perform(
                        delete("/api/v1/catalog/bundle/admin/" + id)
                                .param("expectedVersion", Long.toString(v)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã xóa combo."))
                .andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get("/api/v1/catalog/bundle/admin/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void invalidFieldsNeverPersist() throws Exception {
        var body =
                (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(request("BAD"));
        body.putArray("unknownGroups");
        mvc.perform(
                        post("/api/v1/catalog/bundle/admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/catalog/bundle/admin").param("surprise", "x"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from bundle_configurations", Integer.class))
                .isZero();
    }

    @Test
    void invalidQuantitiesOrVersionsAreRejected() throws Exception {
        for (Object quantity : java.util.List.of(0, -1, "invalid")) {
            var body =
                    (com.fasterxml.jackson.databind.node.ObjectNode)
                            json.valueToTree(request("FRACTION"));
            ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("items").get(0))
                    .set("quantity", json.valueToTree(quantity));
            mvc.perform(
                            post("/api/v1/catalog/bundle/admin")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body.toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SYS_003"));
        }
        var d = create();
        for (Object v : java.util.List.of(-1, "invalid")) {
            var body = json.createObjectNode().put("productStatus", "INACTIVE");
            body.set("expectedVersion", json.valueToTree(v));
            mvc.perform(
                            patch("/api/v1/catalog/bundle/admin/" + d.bundleId() + "/status")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body.toString()))
                    .andExpect(status().isBadRequest());
        }

        assertThat(query.getBundleDetails(d.bundleId()).version()).isEqualTo(d.version());
    }
}
