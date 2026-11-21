package com.aquasuite.ims.listener.finance;

import com.aquasuite.ims.service.finance.PaymentAllocationService;
import com.aquasuite.ims.event.sales.ElectronicInvoiceLinkedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ElectronicInvoiceAllocationListenerTest {

    @Mock
    private PaymentAllocationService paymentAllocationService;

    @InjectMocks
    private ElectronicInvoiceAllocationListener listener;

    @Test
    void onElectronicInvoiceLinkedTriggersAutomaticAllocationForOrder() {
        UUID invoiceId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID linkedBy = UUID.randomUUID();
        ElectronicInvoiceLinkedEvent event = new ElectronicInvoiceLinkedEvent(
                invoiceId, orderId, customerId, linkedBy);

        listener.onElectronicInvoiceLinked(event);

        verify(paymentAllocationService).autoAllocateVerifiedReceiptsForOrder(orderId, linkedBy);
    }
}
