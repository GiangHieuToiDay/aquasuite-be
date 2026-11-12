package com.aquasuite.ims.module.servicedesk;

import com.aquasuite.ims.module.sales.api.event.OrderConfirmedEvent;
import com.aquasuite.ims.module.servicedesk.internal.entity.TechnicalJob;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobStatus;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobType;
import com.aquasuite.ims.module.servicedesk.internal.listener.OrderInstallationJobListener;
import com.aquasuite.ims.module.servicedesk.internal.repository.TechnicalJobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho việc tự sinh công việc lắp đặt từ sự kiện đơn hàng được xác nhận (Giai đoạn 2 - auto từ Order).
 */
@ExtendWith(MockitoExtension.class)
class OrderInstallationJobListenerTest {

    @Mock private TechnicalJobRepository repository;

    private OrderInstallationJobListener listener() {
        return new OrderInstallationJobListener(repository);
    }

    private OrderConfirmedEvent event(String orderType, UUID salesStaffId) {
        return new OrderConfirmedEvent(UUID.randomUUID(), "ORD-1", UUID.randomUUID(),
                orderType, salesStaffId, "12 Ngô Quyền", "Công trình A");
    }

    @Test
    @DisplayName("Đơn PROJECT -> sinh công việc INSTALLATION trạng thái ASSIGNED, gắn orderId")
    void projectOrderCreatesInstallationJob() {
        UUID staff = UUID.randomUUID();
        OrderConfirmedEvent e = event("PROJECT", staff);
        when(repository.existsByOrderIdAndJobType(e.orderId(), TechnicalJobType.INSTALLATION)).thenReturn(false);

        listener().onOrderConfirmed(e);

        ArgumentCaptor<TechnicalJob> captor = ArgumentCaptor.forClass(TechnicalJob.class);
        verify(repository).save(captor.capture());
        TechnicalJob job = captor.getValue();
        assertThat(job.getJobType()).isEqualTo(TechnicalJobType.INSTALLATION);
        assertThat(job.getStatus()).isEqualTo(TechnicalJobStatus.ASSIGNED);
        assertThat(job.getOrderId()).isEqualTo(e.orderId());
        assertThat(job.getTechnicianId()).isNull();
        assertThat(job.getCreatedBy()).isEqualTo(staff);
        assertThat(job.getServiceAddress()).isEqualTo("12 Ngô Quyền");
    }

    @Test
    @DisplayName("Đơn RETAIL -> không sinh công việc")
    void retailOrderSkipped() {
        listener().onOrderConfirmed(event("RETAIL", UUID.randomUUID()));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Đã có job cho đơn -> không sinh trùng (idempotent)")
    void duplicateSkipped() {
        OrderConfirmedEvent e = event("PROJECT", UUID.randomUUID());
        when(repository.existsByOrderIdAndJobType(e.orderId(), TechnicalJobType.INSTALLATION)).thenReturn(true);

        listener().onOrderConfirmed(e);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Thiếu salesStaffId -> bỏ qua (không có người tạo)")
    void missingStaffSkipped() {
        listener().onOrderConfirmed(event("PROJECT", null));
        verify(repository, never()).save(any());
    }
}
