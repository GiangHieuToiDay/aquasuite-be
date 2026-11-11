package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.sales.internal.repository.ReservationAllocationStore;
import com.aquasuite.ims.module.sales.internal.service.*;
import com.aquasuite.ims.share.exception.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationAllocationRunnerTest {
    private final ReservationAllocationProcessor processor = mock(ReservationAllocationProcessor.class);
    private final ReservationAllocationStore jobs = mock(ReservationAllocationStore.class);
    private final OrderStockReservationService reservations = mock(OrderStockReservationService.class);
    private final ReservationAllocationRunner runner = new ReservationAllocationRunner(processor, jobs, reservations);

    @Test void deniedRetryNeverReadsOrExecutesJob() {
        UUID order = UUID.randomUUID();
        when(reservations.get(order)).thenThrow(new AppException(CommonErrorCode.ACCESS_DENIED));
        assertThrows(AppException.class, () -> runner.retry(order, UUID.randomUUID()));
        verifyNoInteractions(jobs, processor);
    }

    @Test void missingOrForeignJobNeverExecutes() {
        UUID order = UUID.randomUUID(), allocation = UUID.randomUUID();
        when(jobs.find(allocation)).thenReturn(Optional.empty());
        assertThrows(AppException.class, () -> runner.retry(order, allocation));
        when(jobs.find(allocation)).thenReturn(Optional.of(new ReservationAllocationStore.Job(allocation, UUID.randomUUID(), "FAILED")));
        assertThrows(AppException.class, () -> runner.retry(order, allocation));
        verifyNoInteractions(processor);
    }

    @Test void retryAuthorizesThenProcessesAndReloadsEvenOnAllocationFailure() {
        UUID order = UUID.randomUUID(), allocation = UUID.randomUUID();
        when(jobs.find(allocation)).thenReturn(Optional.of(new ReservationAllocationStore.Job(allocation, order, "FAILED")));
        doThrow(new IllegalStateException("allocation unavailable")).when(processor).process(allocation);
        assertDoesNotThrow(() -> runner.retry(order, allocation));
        var sequence = inOrder(reservations, jobs, processor);
        sequence.verify(reservations).get(order);
        sequence.verify(jobs).find(allocation);
        sequence.verify(processor).process(allocation);
        sequence.verify(jobs).failed(allocation, "SALES_DEPOSIT_006");
        sequence.verify(reservations).get(order);
    }
}
