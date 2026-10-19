package com.aquasuite.ims.module.catalog.internal.service;

import static org.assertj.core.api.Assertions.*;

import com.aquasuite.ims.module.catalog.internal.payload.BundleCoreStatusRequest;
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
        assertThat(
                        service.status(
                                d.bundleId(),
                                new BundleCoreStatusRequest(
                                        off.version(), BundleCoreStatusRequest.Status.INACTIVE)))
                .isEqualTo(off);
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
        var deleted = service.delete(d.bundleId(), on.version());
        assertThatThrownBy(
                        () ->
                                service.status(
                                        d.bundleId(),
                                        new BundleCoreStatusRequest(
                                                deleted.version(),
                                                BundleCoreStatusRequest.Status.ACTIVE)))
                .isInstanceOf(RuntimeException.class);
    }
}
