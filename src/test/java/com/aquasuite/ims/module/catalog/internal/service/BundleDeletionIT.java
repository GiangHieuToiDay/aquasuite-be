package com.aquasuite.ims.module.catalog.internal.service;

import static org.assertj.core.api.Assertions.*;

import com.aquasuite.ims.support.BundleCoreTestSupport;

import org.junit.jupiter.api.Test;

class BundleDeletionIT extends BundleCoreTestSupport {
    @Test
    void softDeleteRetainsRowsAndCode() {
        var d = create();
        service.deleteBundle(d.bundleId(), d.version());
        assertThat(
                        jdbc.queryForObject(
                                "select status from products where product_id=?",
                                String.class,
                                d.bundleId()))
                .isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("select count(*) from bundle_items", Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from products where product_id=?",
                                Integer.class,
                                d.bundleId()))
                .isEqualTo(1);
        assertThatThrownBy(() -> query.getBundleDetails(d.bundleId()))
                .isInstanceOf(RuntimeException.class);
        assertThat(
                        query.getBundleList(
                                        new com.aquasuite.ims.module.catalog.internal.payload.req
                                                .BundlePageRequest(0, 20, null, null, null))
                                .getTotalElements())
                .isZero();
        assertThatThrownBy(() -> service.createBundle(request(d.productCode())))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void retryUsesCurrentVersion() {
        var d = create();
        service.deleteBundle(d.bundleId(), d.version());
        long deletedVersion =
                jdbc.queryForObject(
                        "select version from bundle_configurations where bundle_product_id=?",
                        Long.class,
                        d.bundleId());
        assertThatThrownBy(() -> service.deleteBundle(d.bundleId(), d.version()))
                .hasMessageContaining("thay đổi");
        service.deleteBundle(d.bundleId(), deletedVersion);
        assertThat(
                        jdbc.queryForObject(
                                "select version from bundle_configurations where"
                                        + " bundle_product_id=?",
                                Long.class,
                                d.bundleId()))
                .isEqualTo(deletedVersion);
    }
}
