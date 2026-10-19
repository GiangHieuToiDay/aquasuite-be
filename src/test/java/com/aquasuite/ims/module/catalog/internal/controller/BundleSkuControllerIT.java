package com.aquasuite.ims.module.catalog.internal.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aquasuite.ims.support.BundleCoreTestSupport;

import org.junit.jupiter.api.Test;

class BundleSkuControllerIT extends BundleCoreTestSupport {
    @Test
    void lookupFiltersVariantsAndBundleParentsBeforePaging() throws Exception {
        jdbc.update(
                "INSERT INTO product_variants(variant_id,product_id,sku,variant_name,unit,"
                        + "conversion_rate,weight,cost_price,selling_price,serial_tracking,status)"
                        + " SELECT ?,product_id,'AAA-INACTIVE',variant_name,unit,conversion_rate,weight,"
                        + "cost_price,selling_price,serial_tracking,'INACTIVE' FROM product_variants"
                        + " WHERE variant_id=?",
                java.util.UUID.randomUUID(), SKU);
        mvc.perform(get("/api/v1/catalog/admin/bundle-skus").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].sku").value("SKU-1"));
        mvc.perform(get("/api/v1/catalog/admin/bundle-skus").param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
        jdbc.update("update products set product_type='BUNDLE' where product_id=?", NORMAL);
        mvc.perform(get("/api/v1/catalog/admin/bundle-skus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void lookupActiveNormalWithoutCostOrStock() throws Exception {
        mvc.perform(get("/api/v1/catalog/admin/bundle-skus").param("keyword", "Normal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].costPrice").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].availableQty").doesNotExist());
        jdbc.update("update products set status='INACTIVE' where product_id=?", NORMAL);
        mvc.perform(get("/api/v1/catalog/admin/bundle-skus"))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }
}
