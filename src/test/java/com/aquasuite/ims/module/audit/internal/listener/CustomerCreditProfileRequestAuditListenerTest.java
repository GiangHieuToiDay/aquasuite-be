package com.aquasuite.ims.module.audit.internal.listener;

import com.aquasuite.ims.module.audit.internal.entity.AuditLog;
import com.aquasuite.ims.module.audit.internal.repository.AuditLogRepository;
import com.aquasuite.ims.module.crm.api.event.CustomerCreditProfileRequestEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerCreditProfileRequestAuditListenerTest {

    @Mock private AuditLogRepository repository;

    @Test
    void writesCustomerTimelineAuditWithoutIdentityEvidenceValues() {
        CustomerCreditProfileRequestAuditListener listener = new CustomerCreditProfileRequestAuditListener(repository);
        UUID eventId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(repository.existsById(eventId)).thenReturn(false);

        listener.onRequestEvent(new CustomerCreditProfileRequestEvent(eventId, requestId, customerId, UUID.randomUUID(),
                "SUBMITTED", new BigDecimal("10000000"), 30, true, Instant.now()));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog audit = captor.getValue();
        assertThat(audit.getEntityType()).isEqualTo("CUSTOMER");
        assertThat(audit.getEntityId()).isEqualTo(customerId);
        assertThat(audit.getNewData()).containsEntry("creditProfileRequestId", requestId)
                .containsEntry("identityEvidenceProvided", true)
                .doesNotContainKeys("identityDocumentNumber", "residentialAddress", "worksiteAddress");
    }
}
