package com.aquasuite.ims.module.catalog.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aquasuite.ims.module.catalog.internal.payload.BundleCoreStatusRequest;
import com.aquasuite.ims.support.BundleCoreTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class BundleCoreHttpIT extends BundleCoreTestSupport {
    @Test
    void sharedItemPayloadKeepsCreateAndUpdateContractsDistinct() throws Exception {
        var detail = create();
        var body = (com.fasterxml.jackson.databind.node.ObjectNode)
                json.valueToTree(request(detail.productCode()));
        body.put("expectedVersion", detail.version());
        mvc.perform(post("/api/v1/catalog/admin/bundles")
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_002"));
        body.remove("expectedVersion");
        mvc.perform(put("/api/v1/catalog/admin/bundles/" + detail.bundleId())
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));
        body.put("expectedVersion", detail.version());
        var item = (com.fasterxml.jackson.databind.node.ObjectNode) body.get("items").get(0);
        item.put("quantity", "1");
        mvc.perform(put("/api/v1/catalog/admin/bundles/" + detail.bundleId())
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_002"));
        item.put("quantity", 1);
        item.put("unexpected", true);
        mvc.perform(put("/api/v1/catalog/admin/bundles/" + detail.bundleId())
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_002"));
        assertThat(service.get(detail.bundleId()).version()).isEqualTo(detail.version());
    }

    @Test
    void adminFiltersAndPagingExcludeDeletedAndNormalProducts() throws Exception {
        var active = service.create(request("FILTER-ACTIVE"));
        var inactive = service.create(request("FILTER-INACTIVE"));
        var removed = service.create(request("FILTER-DELETED"));
        service.status(
                inactive.bundleId(),
                new BundleCoreStatusRequest(
                        inactive.version(), BundleCoreStatusRequest.Status.INACTIVE));
        service.delete(removed.bundleId(), removed.version());
        mvc.perform(get("/api/v1/catalog/admin/bundles").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(
                        get("/api/v1/catalog/admin/bundles")
                                .param("keyword", "  filter-active  ")
                                .param("productStatus", "ACTIVE")
                                .param("publicationStatus", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].bundleId").value(active.bundleId().toString()));
        mvc.perform(get("/api/v1/catalog/admin/bundles").param("publicationStatus", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/v1/catalog/admin/bundles").param("productStatus", "DELETED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_002"));
    }

    @Test
    void deletedBundleCannotBeUpdatedOrToggledEvenWithOldVersion() throws Exception {
        var d = create();
        var deleted = service.delete(d.bundleId(), d.version());
        for (long version : new long[] {d.version(), deleted.version()}) {
            var body =
                    (com.fasterxml.jackson.databind.node.ObjectNode)
                            json.valueToTree(request(d.productCode()));
            body.put("expectedVersion", version);
            mvc.perform(
                            put("/api/v1/catalog/admin/bundles/" + d.bundleId())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SYS_004"));
            mvc.perform(
                            patch("/api/v1/catalog/admin/bundles/" + d.bundleId() + "/status")
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
                        delete("/api/v1/catalog/admin/bundles/" + d.bundleId())
                                .param("expectedVersion", d.version().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_BUNDLE_003"));
        mvc.perform(
                        delete("/api/v1/catalog/admin/bundles/" + d.bundleId())
                                .param("expectedVersion", deleted.version().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(deleted.version().intValue()));
        assertThat(jdbc.queryForObject("select count(*) from bundle_items", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void crudWithoutAuthentication() throws Exception {
        var created =
                mvc.perform(
                                post("/api/v1/catalog/admin/bundles")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsBytes(request("HTTP"))))
                        .andExpect(status().isCreated())
                        .andExpect(header().exists("Location"))
                        .andReturn();
        var d = json.readTree(created.getResponse().getContentAsByteArray()).get("data");
        String id = d.get("bundleId").asText();
        long v = d.get("version").asLong();
        mvc.perform(get("/api/v1/catalog/admin/bundles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/v1/catalog/admin/bundles").param("page", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
        var put = json.valueToTree(request("HTTP"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) put)
                .put("expectedVersion", v)
                .put("name", "Changed");
        var changed =
                mvc.perform(
                                put("/api/v1/catalog/admin/bundles/" + id)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(put.toString()))
                        .andExpect(status().isOk())
                        .andReturn();
        v =
                json.readTree(changed.getResponse().getContentAsByteArray())
                        .at("/data/version")
                        .asLong();
        mvc.perform(get("/api/v1/catalog/admin/bundles/" + id))
                .andExpect(jsonPath("$.data.name").value("Changed"))
                .andExpect(jsonPath("$.data.items[0].costPrice").doesNotExist())
                .andExpect(jsonPath("$.data.availability").doesNotExist());
        var off =
                mvc.perform(
                                patch("/api/v1/catalog/admin/bundles/" + id + "/status")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"expectedVersion\":"
                                                        + v
                                                        + ",\"productStatus\":\"INACTIVE\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        v = json.readTree(off.getResponse().getContentAsByteArray()).at("/data/version").asLong();
        mvc.perform(
                        delete("/api/v1/catalog/admin/bundles/" + id)
                                .param("expectedVersion", Long.toString(v)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productStatus").value("DELETED"));
        mvc.perform(get("/api/v1/catalog/admin/bundles/" + id)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from audit_entries", Integer.class))
                .isZero();
    }

    @Test
    void invalidFieldsNeverPersist() throws Exception {
        var body =
                (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(request("BAD"));
        body.putArray("unknownGroups");
        mvc.perform(
                        post("/api/v1/catalog/admin/bundles")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/catalog/admin/bundles").param("surprise", "x"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from bundle_configurations", Integer.class))
                .isZero();
    }

    @Test
    void fractionalAndStringQuantitiesOrVersionsAreNotCoerced() throws Exception {
        for (Object quantity : java.util.List.of(1.5, 1.0, "1")) {
            var body =
                    (com.fasterxml.jackson.databind.node.ObjectNode)
                            json.valueToTree(request("FRACTION"));
            ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("items").get(0))
                    .set("quantity", json.valueToTree(quantity));
            mvc.perform(
                            post("/api/v1/catalog/admin/bundles")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body.toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SYS_002"));
        }
        var d = create();
        for (Object v : java.util.List.of(0.5, "0")) {
            var body = json.createObjectNode().put("productStatus", "INACTIVE");
            body.set("expectedVersion", json.valueToTree(v));
            mvc.perform(
                            patch("/api/v1/catalog/admin/bundles/" + d.bundleId() + "/status")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body.toString()))
                    .andExpect(status().isBadRequest());
        }

        assertThat(service.get(d.bundleId()).version()).isEqualTo(d.version());
    }
}
