package com.aquasuite.ims.module.fulfillment;

import com.aquasuite.ims.module.fulfillment.internal.service.DeliveryCodQueryService;
import com.aquasuite.ims.module.fulfillment.internal.repository.DeliveryRepository;
import com.aquasuite.ims.module.fulfillment.internal.entity.Delivery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class DeliveryCodQueryServiceTest {
    @Test void lockedSnapshotRefreshesPreviouslyCachedEntityBeforeReturningFacts() {
        var repo=mock(DeliveryRepository.class);var em=mock(EntityManager.class);
        var id=UUID.randomUUID();var d=Delivery.builder().deliveryId(id).deliveryStatus("IN_TRANSIT").codRequired(true).build();
        when(repo.lockById(id)).thenReturn(Optional.of(d));
        doAnswer(x->{d.setDeliveryStatus("DELIVERED");return null;}).when(em).refresh(d,LockModeType.PESSIMISTIC_WRITE);
        var api=new DeliveryCodQueryService(repo,em);
        assertThat(api.lock(id).orElseThrow().status()).isEqualTo("DELIVERED");
        verify(em).refresh(d,LockModeType.PESSIMISTIC_WRITE);
    }
    @Test void missingDeliveryReturnsEmptySnapshot() {
        var repo=mock(DeliveryRepository.class);var em=mock(EntityManager.class);var id=UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThat(new DeliveryCodQueryService(repo,em).find(id)).isEmpty();
        verifyNoInteractions(em);
    }
}
