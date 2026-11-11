package com.aquasuite.ims.module.sales.internal.payload.response;

import com.aquasuite.ims.module.inventory.api.StockReservationApi.Activity;
import com.aquasuite.ims.module.inventory.api.StockReservationApi.Reservation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** UC36 projection deliberately excludes UC26 price snapshots and finance documents. */
public record ReservationManagementView(UUID orderId, List<Reservation> reservations,
        List<Activity> activities, List<Decision> decisions, java.util.Map<UUID, String> actorNames) {
    public record Decision(UUID decisionId, String type, String status, UUID requestedBy,
            OffsetDateTime requestedAt, String reason, UUID reviewedBy, OffsetDateTime reviewedAt,
            String decisionNote, String releaseStatus, ReleaseResult release) {}
    public record ReleaseResult(UUID decisionId, UUID orderId, UUID executedBy, Instant executedAt,
            List<ReleasedLine> lines) {}
    public record ReleasedLine(UUID reservationId, UUID orderItemId, BigDecimal releasedQty) {}
}
