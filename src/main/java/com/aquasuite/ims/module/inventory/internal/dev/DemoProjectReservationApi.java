package com.aquasuite.ims.module.inventory.internal.dev;

import com.aquasuite.ims.module.inventory.api.ProjectReservationApi;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;

/** Finite, simulated stock. Never reads/writes inventories; deliberately single-JVM only. */
@Service
@Profile("uc29-demo & !prod & !production")
public class DemoProjectReservationApi implements ProjectReservationApi {
    private final Clock clock;
    private final Map<StockKey, BigDecimal> stock = new HashMap<>();
    private final Map<UUID, Reservation> reservations = new LinkedHashMap<>();
    private final Map<UUID, Allocation> applied = new HashMap<>();
    private final Map<UUID, List<ReservationActivity>> history = new HashMap<>();

    public DemoProjectReservationApi(@Qualifier("projectDepositClock") Clock clock) { this.clock = clock; }

    @Override
    public synchronized List<Reservation> allocate(Allocation command) {
        Allocation previous = applied.get(command.requestId());
        if (previous != null) {
            if (!previous.equals(command)) throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
            return getReservations(command.orderId());
        }
        validate(command);
        expire();
        Set<UUID> selectedLines = new HashSet<>();
        command.lines().forEach(l -> selectedLines.add(l.orderItemId()));
        // Only the selected lines are covered by this deposit, including their holds at other warehouses.
        if (command.type() == HoldType.DEPOSIT_BACKED) {
            for (Reservation r : List.copyOf(reservations.values())) {
                if (r.orderId().equals(command.orderId()) && selectedLines.contains(r.orderItemId())
                        && r.heldQty().signum() > 0 && r.type() == HoldType.TEMPORARY) {
                    Reservation promoted = new Reservation(r.reservationId(), r.orderId(), r.orderItemId(), r.variantId(),
                            r.warehouseCode(), r.heldQty(), r.consumedQty(), r.releasedQty(), HoldType.DEPOSIT_BACKED, null);
                    reservations.put(r.reservationId(), promoted);
                    log(promoted, command, "PROMOTED", r.heldQty(), r.heldQty());
                }
            }
        }
        for (Line line : command.lines()) {
            BigDecimal committed = reservations.values().stream()
                    .filter(r -> r.orderId().equals(command.orderId()) && r.orderItemId().equals(line.orderItemId()))
                    .map(r -> r.heldQty().add(r.consumedQty())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal missing = line.orderedQty().subtract(committed).max(BigDecimal.ZERO);
            StockKey key = new StockKey(command.warehouseCode(), line.variantId());
            BigDecimal available = stock.getOrDefault(key, BigDecimal.ZERO).subtract(held(key));
            BigDecimal quantity = missing.min(available).max(BigDecimal.ZERO);
            if (quantity.signum() == 0) continue;
            Reservation current = reservations.values().stream().filter(r -> r.orderId().equals(command.orderId())
                    && r.orderItemId().equals(line.orderItemId()) && r.warehouseCode().equals(command.warehouseCode())
                    && r.heldQty().signum() > 0).findFirst().orElse(null);
            BigDecimal before = current == null ? BigDecimal.ZERO : current.heldQty();
            // Do not downgrade an existing deposit-backed hold or refresh an existing temporary deadline.
            HoldType type = current != null && current.type() == HoldType.DEPOSIT_BACKED ? current.type() : command.type();
            Reservation r = new Reservation(current == null ? UUID.randomUUID() : current.reservationId(),
                    command.orderId(), line.orderItemId(), line.variantId(), command.warehouseCode(), before.add(quantity),
                    current == null ? BigDecimal.ZERO : current.consumedQty(), current == null ? BigDecimal.ZERO : current.releasedQty(),
                    type, type == HoldType.DEPOSIT_BACKED ? null : current == null ? command.temporaryExpiresAt() : current.expiresAt());
            reservations.put(r.reservationId(), r);
            log(r, command, "ALLOCATED", before, r.heldQty());
        }
        applied.put(command.requestId(), command);
        return getReservations(command.orderId());
    }

    @Override
    public synchronized List<Reservation> getReservations(UUID orderId) {
        expire();
        return reservations.values().stream().filter(r -> r.orderId().equals(orderId)).toList();
    }

    @Override
    public synchronized List<ReservationActivity> getHistory(UUID orderId) {
        expire();
        return List.copyOf(history.getOrDefault(orderId, List.of()));
    }

    public synchronized StockSnapshot setStock(String warehouse, UUID variantId, BigDecimal onHand) {
        expire();
        StockKey key = new StockKey(warehouse, variantId);
        if (onHand == null || onHand.signum() < 0 || onHand.compareTo(held(key)) < 0) {
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        }
        stock.put(key, onHand);
        return new StockSnapshot(true, warehouse, variantId, onHand, held(key), onHand.subtract(held(key)));
    }

    private BigDecimal held(StockKey key) {
        return reservations.values().stream().filter(r -> r.warehouseCode().equals(key.warehouse) && r.variantId().equals(key.variantId))
                .map(Reservation::heldQty).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void expire() {
        for (Reservation r : List.copyOf(reservations.values())) {
            if (r.type() == HoldType.TEMPORARY && r.heldQty().signum() > 0 && !r.expiresAt().isAfter(clock.instant())) {
                reservations.put(r.reservationId(), new Reservation(r.reservationId(), r.orderId(), r.orderItemId(), r.variantId(),
                        r.warehouseCode(), BigDecimal.ZERO, r.consumedQty(), r.releasedQty().add(r.heldQty()), r.type(), r.expiresAt()));
                history.computeIfAbsent(r.orderId(), ignored -> new ArrayList<>()).add(new ReservationActivity(r.reservationId(),
                        clock.instant(), null, null, "EXPIRED", r.heldQty(), BigDecimal.ZERO));
            }
        }
    }

    private void log(Reservation r, Allocation command, String action, BigDecimal before, BigDecimal after) {
        history.computeIfAbsent(r.orderId(), ignored -> new ArrayList<>()).add(new ReservationActivity(r.reservationId(),
                clock.instant(), command.actorId(), command.requestId(), action, before, after));
    }

    private void validate(Allocation c) {
        Set<UUID> ids = new HashSet<>();
        if (c.requestId() == null || c.orderId() == null || c.actorId() == null || c.type() == null
                || c.warehouseCode() == null || !c.warehouseCode().matches("[A-Z0-9_-]{1,50}") || c.lines().isEmpty()
                || (c.type() == HoldType.TEMPORARY && (c.temporaryExpiresAt() == null || !c.temporaryExpiresAt().isAfter(clock.instant())))
                || (c.type() == HoldType.DEPOSIT_BACKED && c.temporaryExpiresAt() != null)) {
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        }
        for (Line l : c.lines()) {
            if (l.orderItemId() == null || l.variantId() == null || l.orderedQty() == null || l.orderedQty().signum() <= 0 || !ids.add(l.orderItemId())) {
                throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
            }
            if (reservations.values().stream().anyMatch(r -> r.orderId().equals(c.orderId())
                    && r.orderItemId().equals(l.orderItemId()) && !r.variantId().equals(l.variantId()))) {
                throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
            }
            BigDecimal committed = reservations.values().stream().filter(r -> r.orderId().equals(c.orderId())
                            && r.orderItemId().equals(l.orderItemId())
                            && (r.expiresAt() == null || r.expiresAt().isAfter(clock.instant())))
                    .map(r -> r.heldQty().add(r.consumedQty())).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (committed.compareTo(l.orderedQty()) > 0) throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }
    }

    private record StockKey(String warehouse, UUID variantId) {}
    public record StockSnapshot(boolean simulated, String warehouseCode, UUID variantId,
                                BigDecimal onHandQty, BigDecimal reservedQty, BigDecimal availableQty) {}
}
