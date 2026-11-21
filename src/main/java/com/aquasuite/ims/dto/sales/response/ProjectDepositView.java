package com.aquasuite.ims.dto.sales.response;

import com.aquasuite.ims.service.finance.ProjectDepositPaymentService.Payment;
import com.aquasuite.ims.service.inventory.ProjectReservationService.Reservation;
import com.aquasuite.ims.service.inventory.ProjectReservationService.ReservationActivity;
import com.aquasuite.ims.dto.sales.request.ProjectDepositRequests.DeliveryDate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** simulated=true is mandatory for this prototype; no figures represent posted money/stock. */
public record ProjectDepositView(UUID orderId, boolean simulated, BigDecimal totalAmount,
                                 boolean reservationRequested, BigDecimal reservationValue,
                                 BigDecimal minimumDepositPercent, BigDecimal requiredDeposit,
                                 BigDecimal verifiedPaidAmount, BigDecimal outstandingAmount,
                                 boolean depositSatisfied, String reservationSyncStatus,
                                 long scheduleVersion, List<DeliveryDate> deliveries,
                                 List<Payment> payments, List<Reservation> reservations,
                                 List<LineBalance> lines, List<Activity> history,
                                 List<ReservationActivity> reservationHistory) {
    public ProjectDepositView {
        deliveries = List.copyOf(deliveries);
        payments = List.copyOf(payments);
        reservations = List.copyOf(reservations);
        lines = List.copyOf(lines);
        history = List.copyOf(history);
        reservationHistory = List.copyOf(reservationHistory);
    }

    public record LineBalance(UUID orderItemId, UUID variantId, BigDecimal orderedQty, BigDecimal requestedQty,
                              BigDecimal heldQty, BigDecimal consumedQty, BigDecimal waitingQty) {}
    public record Activity(Instant occurredAt, UUID actorId, String action, UUID requestId) {}
}
