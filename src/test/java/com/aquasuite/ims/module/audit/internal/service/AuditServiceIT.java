package com.aquasuite.ims.module.audit.internal.service;

import static org.assertj.core.api.Assertions.*;

import com.aquasuite.ims.module.audit.api.AuditApi;
import com.aquasuite.ims.module.audit.api.dto.AuditCommand;
import com.aquasuite.ims.support.BundleCoreTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

class AuditServiceIT extends BundleCoreTestSupport {
    @Autowired AuditApi audit;

    @Test
    void requiresTransaction() {
        assertThatThrownBy(
                        () ->
                                audit.append(
                                        new AuditCommand(
                                                1L,
                                                "TEST",
                                                UUID.randomUUID(),
                                                null,
                                                null,
                                                Instant.now())))
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void snapshotsHaveOnlyCoreData() {
        var d = create();
        tx.executeWithoutResult(
                status ->
                        audit.append(
                                new AuditCommand(
                                        1L,
                                        "TEST",
                                        d.bundleId(),
                                        null,
                                        json.valueToTree(d),
                                        Instant.now())));
        String after =
                jdbc.queryForObject("select after_value::text from audit_entries", String.class);
        assertThat(after)
                .contains(d.bundleId().toString())
                .doesNotContain("password", "costPrice", "Authorization");
        assertThat(jdbc.queryForObject("select actor_id from audit_entries", Long.class))
                .isEqualTo(1L);
    }
}
