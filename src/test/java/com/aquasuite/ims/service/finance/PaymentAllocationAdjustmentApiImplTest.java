package com.aquasuite.ims.service.finance;
import com.aquasuite.ims.service.finance.impl.PaymentAllocationServiceImpl;

import com.aquasuite.ims.dto.finance.command.OrderModificationSettlementCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@org.junit.jupiter.api.Disabled("Lỗi thời: PaymentAllocationServiceImpl tự xử lý reconcileOrderModification, không còn ủy quyền sang service khác. Cần viết lại test theo logic thật hoặc xóa.")
class PaymentAllocationAdjustmentApiImplTest {

    @Mock
    private PaymentAllocationService paymentAllocationService;

    @InjectMocks
    private PaymentAllocationServiceImpl adjustmentApi;

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
