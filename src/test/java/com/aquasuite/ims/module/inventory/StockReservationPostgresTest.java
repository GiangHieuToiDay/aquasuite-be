package com.aquasuite.ims.module.inventory;

import com.aquasuite.ims.module.inventory.api.StockReservationApi;
import com.aquasuite.ims.module.inventory.api.StockReservationApi.*;
import com.aquasuite.ims.module.inventory.internal.entity.Inventory;
import com.aquasuite.ims.module.inventory.internal.repository.*;
import com.aquasuite.ims.module.inventory.internal.service.impl.StockReservationService;
import com.aquasuite.ims.module.inventory.internal.error.ReservationErrorCode;
import com.aquasuite.ims.module.sales.internal.repository.OrderDepositBalanceRepository;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({StockReservationService.class, StockReservationStore.class, OrderDepositBalanceRepository.class, StockReservationPostgresTest.Json.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StockReservationPostgresTest extends PostgreSqlIntegrationTestSupport {
    @TestConfiguration static class Json {
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
    }
    @Autowired StockReservationApi api;
    @Autowired InventoryRepository inventories;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderDepositBalanceRepository balances;
    @SpyBean StockReservationStore store;
    UUID variant, order, item, actor;
    @BeforeEach void fixture() {
        reset(store);
        variant = UUID.randomUUID(); order = UUID.randomUUID(); item = UUID.randomUUID(); actor = UUID.randomUUID();
        stock("WH_MAIN", 6);
    }
    private void stock(String warehouse, int qty) {
        inventories.saveAndFlush(Inventory.builder().inventoryId(UUID.randomUUID()).variantId(variant)
                .warehouseCode(warehouse).onHandQty(BigDecimal.valueOf(qty)).build());
    }
    private Reserve command(UUID request, String warehouse, String qty) {
        return new Reserve(request, order, item, variant, warehouse, new BigDecimal(qty), actor);
    }
    private BigDecimal reserved(String warehouse) {
        return jdbc.queryForObject("select reserved_qty from inventories where variant_id=? and warehouse_code=?",
                BigDecimal.class, variant, warehouse);
    }
    private void error(Runnable task, ReservationErrorCode expected) {
        assertThatThrownBy(task::run).isInstanceOfSatisfying(AppException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }
    @Test void releasingDeferredHoldNeverReleasesTheLegacyBalance() {
        jdbc.update("update inventories set reserved_qty=2 where variant_id=?",variant);
        var command = command(UUID.randomUUID(),"WH_MAIN","3");
        var held = api.reserveDeferredDeposit(command);
        assertThat(held.heldQty()).isEqualByComparingTo("3");
        assertThat(reserved("WH_MAIN")).isEqualByComparingTo("5");
        assertThat(api.reserveDeferredDeposit(command)).isEqualTo(held);
        api.release(new Release(UUID.randomUUID(),held.reservationId(),new BigDecimal("3"),
                "Release only this order",actor));
        assertThat(reserved("WH_MAIN")).isEqualByComparingTo("2");
        assertThat(api.findByOrder(order).getFirst().heldQty()).isZero();
    }

    @Test void partialAllocationAndIdempotentRetrySurviveServiceCalls() {
        var c = command(UUID.randomUUID(), "WH_MAIN", "10");
        var r = api.reserve(c);
        assertThat(r.heldQty()).isEqualByComparingTo("6");
        assertThat(r.waitingQty()).isEqualByComparingTo("4");
        assertThat(api.reserve(new Reserve(c.requestId(), order, item, variant, "WH_MAIN", new BigDecimal("10.00"), actor)))
                .isEqualTo(r);
        assertThat(reserved("WH_MAIN")).isEqualByComparingTo("6");
        assertThat(jdbc.queryForObject("select on_hand_qty from inventories where variant_id=?", BigDecimal.class, variant))
                .isEqualByComparingTo("6");
        assertThat(api.history(r.reservationId())).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from stock_movements where variant_id=?", Integer.class, variant)).isZero();
        error(() -> api.reserve(command(c.requestId(), "WH_MAIN", "9")), ReservationErrorCode.CONFLICT);
    }
    @Test void threeOfTenUnitsAreHeldAndTheNextReservationCanOnlyTakeSeven() throws Exception {
        jdbc.update("update inventories set on_hand_qty=10 where variant_id=?",variant);
        var c=command(UUID.randomUUID(),"WH_MAIN","3");
        var held=api.reserveForDeposit(c);
        assertThat(held.status()).isEqualTo("HELD");
        assertThat(held.heldQty()).isEqualByComparingTo("3");
        var stock=api.stockByVariant(variant).getFirst();
        assertThat(stock.onHandQty()).isEqualByComparingTo("10");
        assertThat(stock.heldQty()).isEqualByComparingTo("3");
        assertThat(stock.availableQty()).isEqualByComparingTo("7");
        var json=new ObjectMapper().findAndRegisterModules();
        assertThat(json.readTree(json.writeValueAsString(held)).get("status").asText()).isEqualTo("HELD");
        assertThat(api.reserveForDeposit(c)).isEqualTo(held);
        // Previously persisted results without the additive status property remain replayable.
        var old=json.valueToTree(held);
        ((com.fasterxml.jackson.databind.node.ObjectNode)old).remove("status");
        assertThat(json.treeToValue(old,Reservation.class)).isEqualTo(held);
        var next=api.reserveForDeposit(new Reserve(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                variant,"WH_MAIN",new BigDecimal("8"),actor));
        assertThat(next.heldQty()).isEqualByComparingTo("7");
        assertThat(next.waitingQty()).isEqualByComparingTo("1");
        assertThat(api.stockByVariant(variant).getFirst().availableQty()).isZero();
        assertThat(reserved("WH_MAIN")).isEqualByComparingTo("10");
    }
    @Test void releasingHeldUnitsMakesThoseQuantitiesAvailableAgainInTheSameWarehouse() {
        jdbc.update("update inventories set on_hand_qty=10 where variant_id=?",variant);
        stock("WH_OTHER",10);
        var held=api.reserveForDeposit(command(UUID.randomUUID(),"WH_MAIN","3"));
        var partial=api.release(new Release(UUID.randomUUID(),held.reservationId(),BigDecimal.ONE,"Approved reduction",actor));
        assertThat(partial.status()).isEqualTo("HELD");
        assertThat(partial.heldQty()).isEqualByComparingTo("2");
        assertThat(api.stockByVariant(variant)).anySatisfy(s -> {
            assertThat(s.warehouseCode()).isEqualTo("WH_MAIN");
            assertThat(s.availableQty()).isEqualByComparingTo("8");
        }).anySatisfy(s -> {
            assertThat(s.warehouseCode()).isEqualTo("WH_OTHER");
            assertThat(s.availableQty()).isEqualByComparingTo("10");
        });
        var released=api.release(new Release(UUID.randomUUID(),held.reservationId(),new BigDecimal("2"),"Approved cancellation",actor));
        assertThat(released.status()).isEqualTo("RELEASED");
        assertThat(api.stockByVariant(variant)).allSatisfy(s -> assertThat(s.availableQty()).isEqualByComparingTo("10"));
    }
    @Test void newRequestDoesNotDuplicateAndOtherWarehouseFillsOnlyShortage() {
        api.reserve(command(UUID.randomUUID(), "WH_MAIN", "10"));
        stock("WH_SECOND", 20);
        var second = api.reserve(command(UUID.randomUUID(), "WH_SECOND", "10"));
        assertThat(second.heldQty()).isEqualByComparingTo("4");
        assertThat(second.waitingQty()).isZero();
        api.reserve(command(UUID.randomUUID(), "WH_MAIN", "10"));
        assertThat(reserved("WH_MAIN").add(reserved("WH_SECOND"))).isEqualByComparingTo("10");
        assertThat(api.findByOrder(order)).hasSize(2).allSatisfy(r -> assertThat(r.waitingQty()).isZero());
        error(() -> api.reserve(command(UUID.randomUUID(), "WH_MAIN", "11")), ReservationErrorCode.CONFLICT);
    }
    @Test void replenishThenNewRequestAllocatesRemainder() {
        api.reserve(command(UUID.randomUUID(), "WH_MAIN", "10"));
        jdbc.update("update inventories set on_hand_qty=20 where variant_id=?", variant);
        var r = api.reserve(command(UUID.randomUUID(), "WH_MAIN", "10"));
        assertThat(r.heldQty()).isEqualByComparingTo("10");
        assertThat(r.waitingQty()).isZero();
    }
    @Test void releaseIsExactIdempotentAndDoesNotRecreateCancelledUnits() {
        var r = api.reserve(command(UUID.randomUUID(), "WH_MAIN", "6"));
        var c = new Release(UUID.randomUUID(), r.reservationId(), new BigDecimal("2"), "Approved reduction", actor);
        var released = api.release(c);
        assertThat(api.release(c)).isEqualTo(released);
        assertThat(released.heldQty()).isEqualByComparingTo("4");
        assertThat(released.releasedQty()).isEqualByComparingTo("2");
        assertThat(reserved("WH_MAIN")).isEqualByComparingTo("4");
        assertThat(api.reserve(command(UUID.randomUUID(), "WH_MAIN", "6")).heldQty()).isEqualByComparingTo("4");
        error(() -> api.release(new Release(UUID.randomUUID(), r.reservationId(), BigDecimal.TEN, "reason", actor)),
                ReservationErrorCode.EXCESS_RELEASE);
    }
    @Test void inventoryAndDetailRollbackWhenHistoryWriteFails() {
        doThrow(new IllegalStateException("simulated ledger failure")).when(store)
                .record(any(), any(), any(), any(), any(), any(), any(), any());
        assertThatThrownBy(() -> api.reserve(command(UUID.randomUUID(), "WH_MAIN", "6"))).isInstanceOf(IllegalStateException.class);
        assertThat(reserved("WH_MAIN")).isZero();
        assertThat(api.findByOrder(order)).isEmpty();
    }
    @Test void releaseRollbackPreservesHeldStockWhenHistoryFails() {
        var r = api.reserve(command(UUID.randomUUID(), "WH_MAIN", "6"));
        doThrow(new IllegalStateException("simulated ledger failure")).when(store)
                .record(any(), any(), any(), any(), any(), any(), any(), any());
        assertThatThrownBy(() -> api.release(new Release(UUID.randomUUID(), r.reservationId(), BigDecimal.ONE, "reason", actor)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reserved("WH_MAIN")).isEqualByComparingTo("6");
        assertThat(api.findByOrder(order).getFirst().releasedQty()).isZero();
        assertThat(api.history(r.reservationId())).hasSize(1);
    }
    @Test void unavailableStockRemainsWaitingWithoutBorrowingOtherWarehouse() {
        stock("WH_EMPTY", 0);
        var r = api.reserve(command(UUID.randomUUID(), "WH_EMPTY", "6"));
        assertThat(r.heldQty()).isZero();
        assertThat(r.waitingQty()).isEqualByComparingTo("6");
        assertThat(reserved("WH_MAIN")).isZero();
        var changedSku = new Reserve(UUID.randomUUID(), order, item, UUID.randomUUID(), "WH_EMPTY", new BigDecimal("6"), actor);
        error(() -> api.reserve(changedSku), ReservationErrorCode.CONFLICT);
    }
    @Test void concurrentOrdersCannotOversell() throws Exception {
        var a = command(UUID.randomUUID(), "WH_MAIN", "5");
        var b = new Reserve(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), variant, "WH_MAIN", new BigDecimal("5"), actor);
        var result = race(a,b);
        assertThat(result.stream().map(Reservation::heldQty).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("6");
        assertThat(reserved("WH_MAIN")).isEqualByComparingTo("6");
    }
    @Test void concurrentRetryCreatesOnlyOneAllocation() throws Exception {
        var c = command(UUID.randomUUID(), "WH_MAIN", "5");
        var result = race(c,c);
        assertThat(result.getFirst()).isEqualTo(result.getLast());
        assertThat(api.findByOrder(order)).hasSize(1);
        assertThat(api.history(result.getFirst().reservationId())).hasSize(1);
        assertThat(reserved("WH_MAIN")).isEqualByComparingTo("5");
    }
    @Test void concurrentSameLineDifferentWarehousesRespectsSharedTarget() throws Exception {
        stock("WH_SECOND", 10);
        var result = race(command(UUID.randomUUID(), "WH_MAIN", "5"), command(UUID.randomUUID(), "WH_SECOND", "5"));
        assertThat(result.stream().map(Reservation::heldQty).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("5");
    }
    private List<Reservation> race(Reserve a, Reserve b) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var f1 = pool.submit(() -> { start.await(); return api.reserve(a); });
            var f2 = pool.submit(() -> { start.await(); return api.reserve(b); });
            start.countDown();
            return List.of(f1.get(20, TimeUnit.SECONDS), f2.get(20, TimeUnit.SECONDS));
        }
    }
    @Test void validatesPrecisionReasonAndMissingReservation() {
        error(() -> api.reserve(command(UUID.randomUUID(), "WH_MAIN", "1.001")), ReservationErrorCode.INVALID);
        error(() -> api.reserve(command(UUID.randomUUID(), "WH_MAIN", "0")), ReservationErrorCode.INVALID);
        error(() -> api.release(new Release(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, " ", actor)), ReservationErrorCode.INVALID);
        error(() -> api.release(new Release(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, "reason", actor)), ReservationErrorCode.NOT_FOUND);
    }
    @Test void migrationProvidesNonnegativePaidAmountWithoutChangingUpstreamOrder() {
        assertThat(jdbc.queryForObject("select column_default from information_schema.columns where table_schema='public' and table_name='orders' and column_name='paid_amount'", String.class))
                .contains("0");
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conname='chk_orders_paid_amount'", Integer.class)).isEqualTo(1);
    }
    @Test void additivePaymentProjectionReadsNewOrderDefaultsAndPartialOrExcessPayments() {
        UUID customer = UUID.randomUUID();
        jdbc.update("insert into customers(customer_id,customer_code,customer_name,phone) values(?,?,?,?)",
                customer, "TEST-" + customer, "UC29 test", "0901234567");
        try {
            jdbc.update("""
                    insert into orders(order_id,order_code,customer_id,order_channel,order_status,payment_status,order_date,total_amount)
                    values(?,?,?,'IN_STORE','CONFIRMED','UNPAID',current_timestamp,100)
                    """, order, "TEST-" + order, customer);
            assertThat(balances.findByOrderId(order).orElseThrow().paidAmount()).isZero();
            assertThat(balances.findByOrderId(order).orElseThrow().derivedPaymentStatus()).isEqualTo("UNPAID");
            jdbc.update("update orders set paid_amount=20 where order_id=?", order);
            var partial = balances.findByOrderId(order).orElseThrow();
            assertThat(partial.derivedPaymentStatus()).isEqualTo("PARTIALLY_PAID");
            assertThat(partial.outstandingAmount()).isEqualByComparingTo("80");
            jdbc.update("update orders set paid_amount=120 where order_id=?", order);
            assertThat(balances.findByOrderId(order).orElseThrow().derivedPaymentStatus()).isEqualTo("PAID");
            assertThat(balances.findByOrderId(order).orElseThrow().outstandingAmount()).isZero();
            assertThatThrownBy(() -> jdbc.update("update orders set paid_amount=-1 where order_id=?", order))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            jdbc.update("delete from orders where order_id=?", order);
            jdbc.update("delete from customers where customer_id=?", customer);
        }
    }
}
