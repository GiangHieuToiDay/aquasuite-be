package com.aquasuite.ims.module.catalog.internal.service;

import static org.assertj.core.api.Assertions.*;

import com.aquasuite.ims.module.catalog.internal.payload.bundle.request.BundleCoreStatusRequest;
import com.aquasuite.ims.support.BundleCoreTestSupport;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.*;

class BundleConcurrencyIT extends BundleCoreTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"update", "delete", "status"})
    void competingWrites(String competitor) throws Exception {
        var d = create();
        var gate = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> first =
                    () -> {
                        gate.await(10, TimeUnit.SECONDS);
                        try {
                            service.update(d.bundleId(), update(d));
                            return true;
                        } catch (com.aquasuite.ims.share.exception.AppException e) {
                            assertThat(e.getErrorCode().getCode())
                                    .isIn("CATALOG_BUNDLE_003", "SYS_004");
                            return false;
                        }
                    };
            Callable<Boolean> second =
                    () -> {
                        gate.await(10, TimeUnit.SECONDS);
                        try {
                            switch (competitor) {
                                case "delete" -> service.delete(d.bundleId(), d.version());
                                case "status" ->
                                        service.status(
                                                d.bundleId(),
                                                new BundleCoreStatusRequest(
                                                        d.version(),
                                                        BundleCoreStatusRequest.Status.INACTIVE));
                                default -> service.update(d.bundleId(), update(d));
                            }
                            return true;
                        } catch (com.aquasuite.ims.share.exception.AppException e) {
                            assertThat(e.getErrorCode().getCode())
                                    .isIn("CATALOG_BUNDLE_003", "SYS_004");
                            return false;
                        }
                    };
            var a = pool.submit(first);
            var b = pool.submit(second);
            assertThat(
                            (a.get(20, TimeUnit.SECONDS) ? 1 : 0)
                                    + (b.get(20, TimeUnit.SECONDS) ? 1 : 0))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from audit_entries", Integer.class))
                    .isZero();
        } finally {
            pool.shutdownNow();
        }
    }
}
