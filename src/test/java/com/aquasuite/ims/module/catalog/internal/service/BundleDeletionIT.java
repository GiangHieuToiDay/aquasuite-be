package com.aquasuite.ims.module.catalog.internal.service;

import static org.assertj.core.api.Assertions.*;

import com.aquasuite.ims.support.BundleCoreTestSupport;

import org.junit.jupiter.api.Test;

class BundleDeletionIT extends BundleCoreTestSupport {
    @Test
    void softDeleteRetainsRowsAndCode() {
        var d = create();
        service.delete(d.bundleId(), d.version());
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
        assertThatThrownBy(() -> service.get(d.bundleId())).isInstanceOf(RuntimeException.class);
        assertThat(service.list(0, 20, null, null, null).totalElements()).isZero();
        assertThatThrownBy(() -> service.create(request(d.productCode())))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void retryUsesCurrentVersion() {
        var d = create();
        service.delete(d.bundleId(), d.version());
        long deletedVersion =
                jdbc.queryForObject(
                        "select version from bundle_configurations where bundle_product_id=?",
                        Long.class,
                        d.bundleId());
        assertThatThrownBy(() -> service.delete(d.bundleId(), d.version()))
                .hasMessageContaining("thay đổi");
        service.delete(d.bundleId(), deletedVersion);
        assertThat(
                        jdbc.queryForObject(
                                "select version from bundle_configurations where bundle_product_id=?",
                                Long.class,
                                d.bundleId()))
                .isEqualTo(deletedVersion);
        assertThat(jdbc.queryForObject("select count(*) from audit_entries", Integer.class))
                .isZero();
    }
}
