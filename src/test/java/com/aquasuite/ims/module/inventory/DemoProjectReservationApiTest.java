package com.aquasuite.ims.module.inventory;

import com.aquasuite.ims.module.inventory.api.ProjectReservationApi.*;
import com.aquasuite.ims.module.inventory.internal.dev.DemoProjectReservationApi;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class DemoProjectReservationApiTest {
    private final UUID variant = UUID.randomUUID(), actor = UUID.randomUUID();
    private final AdjustableClock clock = new AdjustableClock();
    private final DemoProjectReservationApi api = new DemoProjectReservationApi(clock);
    private Allocation command(UUID request, UUID order, UUID line, String warehouse, HoldType type) {
        return new Allocation(request, order, warehouse, type, type == HoldType.TEMPORARY ? clock.instant().plus(Duration.ofHours(48)) : null,
                List.of(new Line(line, variant, new BigDecimal("10"))), actor);
    }

    @Test
    void concurrentOrdersCannotOverallocateFiniteStock() throws Exception {
        api.setStock("WH_MAIN", variant, new BigDecimal("6"));
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<List<Reservation>>> futures = new ArrayList<>();
            for (int i=0;i<8;i++) {
                Allocation c = command(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "WH_MAIN", HoldType.TEMPORARY);
                futures.add(executor.submit(() -> api.allocate(c)));
            }
            BigDecimal sum = BigDecimal.ZERO;
            for (Future<List<Reservation>> f : futures) for (Reservation r : f.get(5, TimeUnit.SECONDS)) sum = sum.add(r.heldQty());
            assertThat(sum).isEqualByComparingTo("6");
            assertThat(api.setStock("WH_MAIN", variant, new BigDecimal("6")).availableQty()).isZero();
        }
    }

    @Test
    void expiryReleasesOnlyTemporaryStockAndReplayDoesNotRestoreAnExpiredHold() {
        UUID order = UUID.randomUUID(), line = UUID.randomUUID();
        api.setStock("WH_MAIN", variant, new BigDecimal("6"));
        Allocation c = command(UUID.randomUUID(), order, line, "WH_MAIN", HoldType.TEMPORARY);
        api.allocate(c);
        clock.advance(Duration.ofHours(48));
        var expired = api.allocate(c);
        assertThat(expired.getFirst().heldQty()).isZero();
        assertThat(expired.getFirst().releasedQty()).isEqualByComparingTo("6");
        assertThat(api.getHistory(order)).filteredOn(h -> h.action().equals("EXPIRED")).hasSize(1);
        api.allocate(command(UUID.randomUUID(), order, line, "WH_MAIN", HoldType.DEPOSIT_BACKED));
        clock.advance(Duration.ofDays(90));
        assertThat(api.getReservations(order).stream().map(Reservation::heldQty).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("6");
    }

    @Test
    void promotionReusesHoldsAcrossWarehousesAndNewKeysDoNotExceedOrderDemand() {
        UUID order = UUID.randomUUID(), line = UUID.randomUUID();
        api.setStock("WH_A", variant, new BigDecimal("6"));
        api.setStock("WH_B", variant, new BigDecimal("6"));
        var first = api.allocate(command(UUID.randomUUID(), order, line, "WH_A", HoldType.TEMPORARY));
        api.allocate(command(UUID.randomUUID(), order, line, "WH_B", HoldType.TEMPORARY));
        var promoted = api.allocate(command(UUID.randomUUID(), order, line, "WH_B", HoldType.DEPOSIT_BACKED));
        assertThat(promoted).hasSize(2).allMatch(r -> r.type() == HoldType.DEPOSIT_BACKED && r.expiresAt() == null);
        assertThat(promoted).anyMatch(r -> r.reservationId().equals(first.getFirst().reservationId()));
        assertThat(promoted.stream().map(Reservation::heldQty).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("10");
        api.allocate(command(UUID.randomUUID(), order, line, "WH_A", HoldType.TEMPORARY));
        assertThat(api.getReservations(order)).allMatch(r -> r.type() == HoldType.DEPOSIT_BACKED);
    }

    @Test
    void malformedBatchDoesNotPartiallyPromoteOrAllocateAndChangingRequestIsRejected() {
        UUID order = UUID.randomUUID(), line = UUID.randomUUID(), request = UUID.randomUUID();
        api.setStock("WH_MAIN", variant, new BigDecimal("6"));
        Allocation c = command(request, order, line, "WH_MAIN", HoldType.TEMPORARY);
        api.allocate(c);
        assertThatThrownBy(() -> api.allocate(command(request, order, line, "WH_MAIN", HoldType.DEPOSIT_BACKED))).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> api.allocate(new Allocation(UUID.randomUUID(), order, "WH_MAIN", HoldType.DEPOSIT_BACKED,
                null, List.of(new Line(line, variant, BigDecimal.TEN), new Line(line, variant, BigDecimal.TEN)), actor)))
                .isInstanceOf(AppException.class);
        assertThat(api.getReservations(order).getFirst().type()).isEqualTo(HoldType.TEMPORARY);
        assertThatThrownBy(() -> api.setStock("WH_MAIN", variant, new BigDecimal("5"))).isInstanceOf(AppException.class);
    }

    @Test
    void depositOnlyPromotesSelectedLinesAndLeavesOtherTemporaryHoldsAlone() {
        UUID order = UUID.randomUUID(), selected = UUID.randomUUID(), other = UUID.randomUUID();
        api.setStock("WH_MAIN", variant, new BigDecimal("20"));
        api.allocate(command(UUID.randomUUID(), order, selected, "WH_MAIN", HoldType.TEMPORARY));
        api.allocate(command(UUID.randomUUID(), order, other, "WH_MAIN", HoldType.TEMPORARY));
        var result = api.allocate(command(UUID.randomUUID(), order, selected, "WH_MAIN", HoldType.DEPOSIT_BACKED));
        assertThat(result).filteredOn(r -> r.orderItemId().equals(selected)).allMatch(r -> r.type() == HoldType.DEPOSIT_BACKED);
        assertThat(result).filteredOn(r -> r.orderItemId().equals(other)).allMatch(r -> r.type() == HoldType.TEMPORARY);
    }

    private static final class AdjustableClock extends Clock {
        private Instant now = Instant.parse("2026-10-31T10:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
