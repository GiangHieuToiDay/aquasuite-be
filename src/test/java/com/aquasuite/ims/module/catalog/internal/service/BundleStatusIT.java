package com.aquasuite.ims.module.catalog.internal.service;

import static org.assertj.core.api.Assertions.*;

import com.aquasuite.ims.module.catalog.internal.payload.req.BundleCoreStatusRequest;
import com.aquasuite.ims.support.BundleCoreTestSupport;

import org.junit.jupiter.api.Test;

class BundleStatusIT extends BundleCoreTestSupport {
    @Test
    void toggleNoOpAndDeleted() {
        var d = create();
        var off =
                service.status(
                        d.bundleId(),
                        new BundleCoreStatusRequest(
                                d.version(), BundleCoreStatusRequest.Status.INACTIVE));
        assertThat(off.publicationStatus()).isEqualTo("DRAFT");
        var persistedOff = service.get(d.bundleId());
        assertThat(off)
                .usingRecursiveComparison()
                .ignoringFields("updatedAt")
                .isEqualTo(persistedOff);
        assertThat(off.updatedAt())
                .isCloseTo(
                        persistedOff.updatedAt(), within(1, java.time.temporal.ChronoUnit.MICROS));
        assertThat(off.items()).isEqualTo(d.items());
        assertThat(
                        service.status(
                                d.bundleId(),
                                new BundleCoreStatusRequest(
                                        off.version(), BundleCoreStatusRequest.Status.INACTIVE)))
                .isEqualTo(persistedOff);
        assertThatThrownBy(
                        () ->
                                service.status(
                                        d.bundleId(),
                                        new BundleCoreStatusRequest(
                                                d.version(),
                                                BundleCoreStatusRequest.Status.ACTIVE)))
                .hasMessageContaining("thay đổi");
        var on =
                service.status(
                        d.bundleId(),
                        new BundleCoreStatusRequest(
                                off.version(), BundleCoreStatusRequest.Status.ACTIVE));
        service.delete(d.bundleId(), on.version());
        assertThatThrownBy(
                        () ->
                                service.status(
                                        d.bundleId(),
                                        new BundleCoreStatusRequest(
                                                on.version() + 1,
                                                BundleCoreStatusRequest.Status.ACTIVE)))
                .isInstanceOf(RuntimeException.class);
    }
}
