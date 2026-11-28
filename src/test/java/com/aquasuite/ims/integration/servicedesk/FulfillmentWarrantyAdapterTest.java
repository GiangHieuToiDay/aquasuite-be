package com.aquasuite.ims.integration.servicedesk;

import com.aquasuite.ims.entity.fulfillment.Delivery;
import com.aquasuite.ims.repository.fulfillment.DeliveryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillmentWarrantyAdapterTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Test
    @DisplayName("findDeliverySummary: ánh xạ thông tin phiếu giao hàng thực tế và xử lý null")
    void mapsRealDeliverySummary() {
        FulfillmentWarrantyAdapter adapter = new FulfillmentWarrantyAdapter(deliveryRepository);
        UUID deliveryId = UUID.randomUUID();
        OffsetDateTime deliveredAt = OffsetDateTime.parse("2026-11-12T10:15:00+07:00");
        Delivery delivery = Delivery.builder()
                .deliveryId(deliveryId)
                .deliveryCode("DN-202611-0001")
                .deliveryStatus("DELIVERED")
                .deliveredAt(deliveredAt)
                .note("Giao đủ phụ kiện")
                .build();

        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));

        var result = adapter.findDeliverySummary(deliveryId);
        assertThat(result).isPresent();
        assertThat(result.get().deliveryId()).isEqualTo(deliveryId);
        assertThat(result.get().deliveryCode()).isEqualTo("DN-202611-0001");
        assertThat(result.get().status()).isEqualTo("DELIVERED");
        assertThat(result.get().deliveredAt()).isEqualTo(deliveredAt);
        assertThat(result.get().note()).isEqualTo("Giao đủ phụ kiện");

        assertThat(adapter.findDeliverySummary(null)).isEmpty();
    }
}
