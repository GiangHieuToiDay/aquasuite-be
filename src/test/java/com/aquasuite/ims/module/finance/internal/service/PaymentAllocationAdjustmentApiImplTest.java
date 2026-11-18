package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.api.dto.OrderModificationSettlementCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentAllocationAdjustmentApiImplTest {

    @Mock
    private PaymentAllocationService paymentAllocationService;

    @InjectMocks
    private PaymentAllocationAdjustmentApiImpl adjustmentApi;

    @Test
    void reconcileOrderModificationDelegatesFullCancellationCommandToService() {
        OrderModificationSettlementCommand command = new OrderModificationSettlementCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Hủy toàn bộ đơn hàng và hoàn tiền đã xác minh");

        adjustmentApi.reconcileOrderModification(command);

        verify(paymentAllocationService).reconcileOrderModification(command);
    }

    @Test
    void reconcileOrderModificationDelegatesReplacementOrderCommandToService() {
        OrderModificationSettlementCommand command = new OrderModificationSettlementCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                "Điều chuyển phân bổ sang đơn thay thế");

        adjustmentApi.reconcileOrderModification(command);

        verify(paymentAllocationService).reconcileOrderModification(command);
    }
}
