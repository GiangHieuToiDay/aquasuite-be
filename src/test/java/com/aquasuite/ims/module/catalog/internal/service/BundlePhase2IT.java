package com.aquasuite.ims.module.catalog.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aquasuite.ims.module.catalog.internal.payload.req.*;
import com.aquasuite.ims.module.catalog.internal.payload.res.BundleResponse;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.support.BundleCoreTestSupport;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

class BundlePhase2IT extends BundleCoreTestSupport {
    CreateBundleRequest definition(String code, Instant from, Instant until) {
        var r = request(code);
        return new CreateBundleRequest(
                r.productCode(),
                r.name(),
                r.categoryId(),
                null,
                null,
                r.items(),
                r.packagePrice(),
                from,
                until);
    }

    BundleResponse published() {
        var now = Instant.now();
        var d =
                service.createBundle(
                        definition("PUBLIC", now.minusSeconds(60), now.plusSeconds(3600)));
        return service.changePublicationStatus(
                d.bundleId(),
                new ChangePublicationStatusRequest(
                        ChangePublicationStatusRequest.Status.PUBLISHED, d.version()));
    }

    @Test
    void publicationChecksVersionBeforeNoopAndUnpublishHides() {
        var d = published();
        assertThat(
                        service.changePublicationStatus(
                                        d.bundleId(),
                                        new ChangePublicationStatusRequest(
                                                ChangePublicationStatusRequest.Status.PUBLISHED,
                                                d.version()))
                                .version())
                .isEqualTo(d.version());
        assertThatThrownBy(
                        () ->
                                service.changePublicationStatus(
                                        d.bundleId(),
                                        new ChangePublicationStatusRequest(
                                                ChangePublicationStatusRequest.Status.PUBLISHED,
                                                d.version() - 1)))
                .isInstanceOf(AppException.class);
        var h =
                service.changePublicationStatus(
                        d.bundleId(),
                        new ChangePublicationStatusRequest(
                                ChangePublicationStatusRequest.Status.UNPUBLISHED, d.version()));
        assertThat(h.version()).isGreaterThan(d.version());
        assertThatThrownBy(() -> query.getPublishedBundleDetails(d.bundleId()))
                .isInstanceOf(AppException.class);
    }

    @Test
    void draftNeedsDatesAndExpiredCannotPublish() {
        var d = create();
        assertThatThrownBy(
                        () ->
                                service.changePublicationStatus(
                                        d.bundleId(),
                                        new ChangePublicationStatusRequest(
                                                ChangePublicationStatusRequest.Status.PUBLISHED,
                                                d.version())))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(
                        () ->
                                service.changePublicationStatus(
                                        d.bundleId(),
                                        new ChangePublicationStatusRequest(
                                                ChangePublicationStatusRequest.Status.UNPUBLISHED,
                                                d.version())))
                .isInstanceOf(AppException.class);
        var now = Instant.now();
        var expired =
                service.createBundle(
                        definition("EXPIRED", now.minusSeconds(120), now.minusSeconds(60)));
        assertThatThrownBy(
                        () ->
                                service.changePublicationStatus(
                                        expired.bundleId(),
                                        new ChangePublicationStatusRequest(
                                                ChangePublicationStatusRequest.Status.PUBLISHED,
                                                expired.version())))
                .isInstanceOf(AppException.class);
    }

    @Test
    void visibilityFilteredBeforePaginationAndNoOptionFields() throws Exception {
        var d = published();
        create();
        var now = Instant.now();
        var future =
                service.createBundle(
                        definition("FUTURE", now.plusSeconds(600), now.plusSeconds(1200)));
        service.changePublicationStatus(
                future.bundleId(),
                new ChangePublicationStatusRequest(
                        ChangePublicationStatusRequest.Status.PUBLISHED, future.version()));
        var page = query.getPublishedBundles(new BundlePageRequest(0, 1, null, null, null));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getItems().getFirst().bundleId()).isEqualTo(d.bundleId());
        mvc.perform(get("/api/v1/catalog/bundle/" + future.bundleId()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/catalog/bundle/" + d.bundleId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.componentGroups").doesNotExist())
                .andExpect(jsonPath("$.data.costPrice").doesNotExist());
    }

    @Test
    void fixedDemandAndKnownShortageReportedWithoutReservation() {
        var d = published();
        jdbc.update(
                "insert into"
                    + " inventories(inventory_id,variant_id,warehouse_code,on_hand_qty,reserved_qty,minimum_qty,updated_at)"
                    + " values(?,?,'WH_MAIN',3,1,0,CURRENT_TIMESTAMP)",
                UUID.randomUUID(),
                SKU);
        var r =
                evaluation.checkAvailability(
                        d.bundleId(), new BundleEvaluationRequest(2, d.version()));
        assertThat(r.availability()).isEqualTo("AVAILABLE");
        assertThat(r.purchasable()).isTrue();
        var shortResult =
                evaluation.checkAvailability(
                        d.bundleId(), new BundleEvaluationRequest(3, d.version()));
        assertThat(shortResult.availability()).isEqualTo("UNAVAILABLE");
        assertThat(shortResult.missingVariantIds()).containsExactly(SKU);
        assertThat(
                        jdbc.queryForObject(
                                "select reserved_qty from inventories where variant_id=?",
                                BigDecimal.class,
                                SKU))
                .isEqualByComparingTo("1");
    }

    @Test
    void missingInventoryUnavailableAndStalePreviewRejected() {
        var d = published();
        var r = evaluation.checkAvailability(d.bundleId(), new BundleEvaluationRequest(1, null));
        assertThat(r.availability()).isEqualTo("UNAVAILABLE");
        assertThat(r.purchasable()).isFalse();
        assertThatThrownBy(
                        () ->
                                evaluation.checkAvailability(
                                        d.bundleId(),
                                        new BundleEvaluationRequest(1, d.version() - 1)))
                .isInstanceOf(AppException.class);
    }

    @Test
    void publishedEditRejectedAndUnpublishedUpdateReplacesDefinition() {
        var d = published();
        assertThatThrownBy(() -> service.updateBundle(d.bundleId(), update(d)))
                .isInstanceOf(AppException.class);
        var h =
                service.changePublicationStatus(
                        d.bundleId(),
                        new ChangePublicationStatusRequest(
                                ChangePublicationStatusRequest.Status.UNPUBLISHED, d.version()));
        var changed = service.updateBundle(d.bundleId(), update(h));
        assertThat(changed.validFrom()).isNull();
        assertThat(changed.validUntil()).isNull();
        assertThat(changed.items().getFirst().quantity()).isEqualTo(2);
        assertThat(changed.version()).isGreaterThan(h.version());
    }

    @Test
    void patchPublicationAndRetiredRoutes() throws Exception {
        var now = Instant.now();
        var d =
                service.createBundle(
                        definition("PATCH", now.minusSeconds(60), now.plusSeconds(3600)));
        String url = "/api/v1/catalog/bundle/admin/" + d.bundleId();
        mvc.perform(
                        patch(url + "/publication-status")
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                new ChangePublicationStatusRequest(
                                                        ChangePublicationStatusRequest.Status
                                                                .PUBLISHED,
                                                        d.version()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicationStatus").value("PUBLISHED"));
        mvc.perform(
                        post(url + "/publish")
                                .contentType("application/json")
                                .content("{\"expectedVersion\":0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void optionsAndSelectionsRejected() throws Exception {
        var body = json.valueToTree(request("NO-OPTIONS"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).putArray("componentGroups");
        mvc.perform(
                        post("/api/v1/catalog/bundle/admin")
                                .contentType("application/json")
                                .content(body.toString()))
                .andExpect(status().isBadRequest());
        var d = published();
        mvc.perform(
                        post("/api/v1/catalog/bundle/" + d.bundleId() + "/evaluations")
                                .contentType("application/json")
                                .content("{\"quantity\":1,\"selections\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void legacyOptionRowsPreservedButIgnored() throws Exception {
        var d = published();
        UUID group = UUID.randomUUID();
        jdbc.update(
                "insert into"
                    + " bundle_component_groups(bundle_product_id,group_id,default_variant_id,allowed_variant_ids)"
                    + " values(?,?,?,ARRAY[?]::uuid[])",
                d.bundleId(),
                group,
                SKU,
                SKU);
        var h =
                service.changePublicationStatus(
                        d.bundleId(),
                        new ChangePublicationStatusRequest(
                                ChangePublicationStatusRequest.Status.UNPUBLISHED, d.version()));
        service.updateBundle(d.bundleId(), update(h));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from bundle_component_groups where"
                                        + " bundle_product_id=?",
                                Integer.class,
                                d.bundleId()))
                .isEqualTo(1);
        assertThat(json.writeValueAsString(query.getBundleDetails(d.bundleId())))
                .doesNotContain("componentGroups");
    }

    @Test
    void currentPriceDropPreventsPurchase() {
        var d = published();
        jdbc.update("update product_variants set selling_price=1 where variant_id=?", SKU);
        var r = evaluation.checkAvailability(d.bundleId(), new BundleEvaluationRequest(1, null));
        assertThat(r.priceIssue()).isEqualTo("PACKAGE_EXCEEDS_RETAIL");
        assertThat(r.savingAmount()).isNull();
        assertThat(r.purchasable()).isFalse();
    }
}
