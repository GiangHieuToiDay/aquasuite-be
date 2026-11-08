package com.aquasuite.ims.module.sales.internal.payload.response;

import com.aquasuite.ims.module.finance.api.dto.OrderReceivableView;
import com.aquasuite.ims.module.inventory.api.StockReservationApi.Reservation;
import com.aquasuite.ims.module.sales.internal.payload.request.OrderStockReservationRequest.Line;
import java.math.BigDecimal;
import java.util.*;

public record OrderStockReservationView(UUID orderId, boolean reservationRequested,
        BigDecimal reservationValue, OrderReceivableView payment,
        String status, List<Line> lines, List<Reservation> reservations,
        String depositStatus, String allocationStatus, String allocationErrorCode, String warehouseCode, UUID allocationId,
        List<LineStatus> lineStatuses) {
    /** State belongs to the line on this order, never the global catalog product. */
    public record LineStatus(UUID orderItemId, BigDecimal requestedQty, BigDecimal heldQty,
            BigDecimal waitingQty, String status) {}
}
