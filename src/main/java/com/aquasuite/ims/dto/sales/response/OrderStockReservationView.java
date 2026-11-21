package com.aquasuite.ims.dto.sales.response;

import com.aquasuite.ims.dto.finance.response.OrderReceivableView;
import com.aquasuite.ims.service.inventory.StockReservationService.Reservation;
import com.aquasuite.ims.dto.sales.request.OrderStockReservationRequest.Line;
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
