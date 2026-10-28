package com.aquasuite.ims.module.catalog.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aquasuite.ims.module.catalog.internal.payload.req.*;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.support.BundleCoreTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;

class BundleInventoryFailureIT extends BundleCoreTestSupport {
    @MockitoSpyBean InventoryApi inventory;

    @Test
    void failedInventorySqlDoesNotPoisonCatalogTransaction() {
        var r = request("FAILURE");
        var now = Instant.now();
        var d =
                service.createBundle(
                        new CreateBundleRequest(
                                r.productCode(),
                                r.name(),
                                r.categoryId(),
                                null,
                                null,
                                r.items(),
                                r.packagePrice(),
                                now.minusSeconds(60),
                                now.plusSeconds(600)));
        var active =
                service.changePublicationStatus(
                        d.bundleId(),
                        new ChangePublicationStatusRequest(
                                ChangePublicationStatusRequest.Status.PUBLISHED, d.version()));
        doAnswer(invocation -> jdbc.queryForObject("SELECT 1 / 0", Integer.class))
                .when(inventory)
                .getStockSummaries(any());
        var result =
                tx.execute(
                        status -> {
                            var preview =
                                    evaluation.checkAvailability(
                                            active.bundleId(),
                                            new BundleEvaluationRequest(1, null));
                            assertThat(
                                            jdbc.queryForObject(
                                                    "select count(*) from bundle_configurations",
                                                    Integer.class))
                                    .isEqualTo(1);
                            return preview;
                        });
        assertThat(result.availability()).isEqualTo("UNAVAILABLE");
        assertThat(result.purchasable()).isFalse();
    }
}
