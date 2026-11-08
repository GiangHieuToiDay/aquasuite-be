package com.aquasuite.ims.module.catalog.internal.service;

import static org.assertj.core.api.Assertions.*;

import com.aquasuite.ims.module.catalog.internal.payload.req.ChangeBundleStatusRequest;
import com.aquasuite.ims.support.BundleCoreTestSupport;

import org.junit.jupiter.api.Test;

class BundleStatusIT extends BundleCoreTestSupport {
    @Test
    void toggleNoOpAndDeleted() {
        var d = create();
        var off =
                service.changeProductStatus(
                        d.bundleId(),
                        new ChangeBundleStatusRequest(
                                d.version(), ChangeBundleStatusRequest.Status.INACTIVE));
        assertThat(off.publicationStatus()).isEqualTo("DRAFT");
        var persistedOff = query.getBundleDetails(d.bundleId());
        assertThat(off)
                .usingRecursiveComparison()
                .ignoringFields("updatedAt")
                .isEqualTo(persistedOff);
        assertThat(off.updatedAt())
                .isCloseTo(
                        persistedOff.updatedAt(), within(1, java.time.temporal.ChronoUnit.MICROS));
        assertThat(off.items()).isEqualTo(d.items());
        assertThat(
                        service.changeProductStatus(
                                d.bundleId(),
                                new ChangeBundleStatusRequest(
                                        off.version(), ChangeBundleStatusRequest.Status.INACTIVE)))
                .isEqualTo(persistedOff);
        assertThatThrownBy(
                        () ->
                                service.changeProductStatus(
                                        d.bundleId(),
                                        new ChangeBundleStatusRequest(
                                                d.version(),
                                                ChangeBundleStatusRequest.Status.ACTIVE)))
                .hasMessageContaining("thay đổi");
        var on =
                service.changeProductStatus(
                        d.bundleId(),
                        new ChangeBundleStatusRequest(
                                off.version(), ChangeBundleStatusRequest.Status.ACTIVE));
        service.deleteBundle(d.bundleId(), on.version());
        assertThatThrownBy(
                        () ->
                                service.changeProductStatus(
                                        d.bundleId(),
                                        new ChangeBundleStatusRequest(
                                                on.version() + 1,
                                                ChangeBundleStatusRequest.Status.ACTIVE)))
                .isInstanceOf(RuntimeException.class);
    }
}
