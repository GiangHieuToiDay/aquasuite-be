package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.finance.api.OrderReceivableApi;
import com.aquasuite.ims.module.finance.internal.service.impl.OrderReceivableApiImpl;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.inventory.internal.repository.*;
import com.aquasuite.ims.module.inventory.internal.service.impl.StockReservationService;
import com.aquasuite.ims.module.inventory.internal.service.impl.InventoryServiceImpl;
import com.aquasuite.ims.module.inventory.internal.error.ReservationErrorCode;
import com.aquasuite.ims.module.sales.internal.service.impl.SalesOrderApiImpl;
import com.aquasuite.ims.module.sales.internal.payload.request.OrderStockReservationRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.OrderStockReservationRequest.Line;
import com.aquasuite.ims.module.sales.internal.payload.response.OrderStockReservationView;
import com.aquasuite.ims.module.sales.internal.repository.OrderStockReservationStore;
import com.aquasuite.ims.module.sales.internal.service.OrderStockReservationService;
import com.aquasuite.ims.share.exception.*;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.aquasuite.ims.module.sales.internal.error.ProjectDepositErrorCode.*;

@DataJpaTest(showSql=false, properties="spring.datasource.hikari.maximum-pool-size=3")
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({OrderStockReservationService.class,OrderStockReservationStore.class,OrderReceivableApiImpl.class,SalesOrderApiImpl.class,
        StockReservationService.class,StockReservationStore.class,InventoryServiceImpl.class,
        OrderStockReservationPostgresTest.Json.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class OrderStockReservationPostgresTest extends PostgreSqlIntegrationTestSupport {
    @TestConfiguration static class Json {
        @Bean ObjectMapper json() { return new ObjectMapper().findAndRegisterModules(); }
    }
    @Autowired OrderStockReservationService service;
    @Autowired InventoryServiceImpl legacy;
    @Autowired com.aquasuite.ims.module.inventory.api.StockReservationApi reservationApi;
    @Autowired JdbcTemplate jdbc;
    @MockBean IamApi iam;
    @SpyBean StockReservationStore ledger;
    UUID order,item,variant,customer,actor,inventory;
    @BeforeEach void fixture() {
        reset(ledger,iam);
        order=UUID.randomUUID();item=UUID.randomUUID();variant=UUID.randomUUID();
        customer=UUID.randomUUID();actor=UUID.randomUUID();inventory=UUID.randomUUID();
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(actor).roles(List.of("SHOP_OWNER")).build());
        UUID category=UUID.randomUUID(),product=UUID.randomUUID();
        jdbc.update("insert into categories(category_id,category_code,category_name) values(?,?,?)",category,"C_"+category.toString().replace("-","").toUpperCase(Locale.ROOT),"Test "+category);
        jdbc.update("insert into products(product_id,product_code,category_id,product_name) values(?,?,?,?)",product,"P-"+product,category,"Test");
        jdbc.update("insert into product_variants(variant_id,product_id,sku,variant_name,unit) values(?,?,?,?,?)",variant,product,"SKU-"+variant,"Test","piece");
        jdbc.update("insert into customers(customer_id,customer_code,customer_name,phone) values(?,?,?,?)",customer,"C-"+customer,"Test",String.format("09%08d",Math.floorMod(customer.hashCode(),100000000)));
        jdbc.update("""
                insert into orders(order_id,order_code,customer_id,order_channel,order_status,payment_status,order_date,total_amount,deposit_amount)
                values(?,?,?,'IN_STORE','PROCESSING','UNPAID',current_timestamp,100,20)
                """,order,"O-"+order,customer);
        jdbc.update("insert into order_items(order_item_id,order_id,variant_id,quantity,line_total) values(?,?,?,10,100)",item,order,variant);
        jdbc.update("insert into inventories(inventory_id,variant_id,warehouse_code,on_hand_qty,reserved_qty,minimum_qty,updated_at) values(?,?,'WH_MAIN',6,0,0,current_timestamp)",inventory,variant);
    }
    private void receipt(String amount,String status) {
        UUID id=UUID.randomUUID();
        jdbc.update("""
                insert into transactions(transaction_id,transaction_code,transaction_type,category,customer_id,order_id,
                amount,payment_method,status,transaction_date,created_by) values(?,?,'RECEIPT','SALE',?,?,?,'CASH',?,current_timestamp,?)
                """,id,"T-"+id,customer,order,new BigDecimal(amount),status,actor);
    }
    private OrderStockReservationRequest request(UUID key,String qty) {
        return new OrderStockReservationRequest(key,"WH_MAIN",List.of(new Line(item,new BigDecimal(qty))));
    }
    private BigDecimal reserved() { return jdbc.queryForObject("select reserved_qty from inventories where inventory_id=?",BigDecimal.class,inventory); }
    private int operations() { return jdbc.queryForObject("select count(*) from stock_reservation_operations where reservation_id in (select reservation_id from stock_reservations where source_id=?)",Integer.class,order); }
    private void error(Runnable action,BaseErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class,e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
    @Test void pendingAndInsufficientFundsPersistSelectionWithoutHolding() {
        receipt("20","PENDING");receipt("7","VERIFIED");
        var result=service.apply(order,request(UUID.randomUUID(),"4"),false);
        assertThat(result.payment().depositRequired()).isEqualByComparingTo("20");
        assertThat(result.payment().verifiedPaid()).isEqualByComparingTo("7");
        assertThat(result.status()).isEqualTo("AWAITING_DEPOSIT");
        assertThat(reserved()).isZero();
        assertThat(service.get(order).lines()).hasSize(1);
        assertThat(jdbc.queryForObject("select paid_amount from orders where order_id=?",BigDecimal.class,order)).isZero();
    }
    @Test void verifiedFundsAllocatePartialStockAndRetryDoesNotDuplicate() {
        receipt("20","VERIFIED");
        UUID key=UUID.randomUUID();
        var a=service.apply(order,request(key,"10"),false);
        assertThat(a.payment().depositRequired()).isEqualByComparingTo("20");
        assertThat(a.reservations().getFirst().heldQty()).isEqualByComparingTo("6");
        assertThat(a.reservations().getFirst().waitingQty()).isEqualByComparingTo("4");
        service.apply(order,request(key,"10.00"),false);
        service.apply(order,request(UUID.randomUUID(),"10"),true);
        assertThat(reserved()).isEqualByComparingTo("6");
        assertThat(operations()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select on_hand_qty from inventories where inventory_id=?",BigDecimal.class,inventory)).isEqualByComparingTo("6");
        jdbc.update("update inventories set on_hand_qty=12 where inventory_id=?",inventory);
        var filled=service.apply(order,request(UUID.randomUUID(),"10"),true);
        assertThat(filled.status()).isEqualTo("HELD");
        assertThat(reserved()).isEqualByComparingTo("10");
    }
    @Test void verifiedLaterCanSynchronizeSamePersistedSelection() {
        service.apply(order,request(UUID.randomUUID(),"4"),false);
        receipt("20","VERIFIED");
        assertThat(service.get(order).status()).isEqualTo("RESERVATION_PENDING");
        var result=service.apply(order,request(UUID.randomUUID(),"4"),true);
        assertThat(result.status()).isEqualTo("HELD");
        assertThat(reserved()).isEqualByComparingTo("4");
    }
    @Test void selectedValueDoesNotOverrideTheAgreedOrderDeposit() {
        jdbc.update("update orders set total_amount=80 where order_id=?",order);
        receipt("6.39","VERIFIED");
        var selected=service.apply(order,request(UUID.randomUUID(),"4"),false);
        assertThat(selected.reservationValue()).isEqualByComparingTo("32");
        assertThat(selected.payment().depositRequired()).isEqualByComparingTo("20");
        assertThat(reserved()).isZero();
        receipt("13.61","VERIFIED");
        assertThat(service.apply(order,request(UUID.randomUUID(),"4"),true).status()).isEqualTo("HELD");
    }
    @Test void assignedSalesStaffCanHoldTheirOwnOrder() {
        jdbc.update("insert into users(id,username,email,password_hash,status) values(?,?,?,?, 'ACTIVE')",
                actor,"uc29_"+actor,"uc29_"+actor+"@test.invalid","{disabled-test-account}");
        jdbc.update("update orders set sales_staff_id=? where order_id=?",actor,order);
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(actor).roles(List.of("SALES_STAFF")).build());
        receipt("20","VERIFIED");
        assertThat(service.apply(order,request(UUID.randomUUID(),"4"),false).status()).isEqualTo("HELD");
    }
    @Test void rejectsChangedScopeChangedPriceAndReusedKey() {
        UUID key=UUID.randomUUID();
        service.apply(order,request(key,"4"),false);
        error(() -> service.apply(order,request(key,"5"),false),IDEMPOTENCY_CONFLICT);
        error(() -> service.apply(order,request(UUID.randomUUID(),"5"),false),RESERVATION_SELECTION_CONFLICT);
        jdbc.update("update orders set total_amount=120 where order_id=?",order);
        error(() -> service.apply(order,request(UUID.randomUUID(),"4"),true),RESERVATION_SELECTION_CONFLICT);
    }
    @Test void rejectsOtherStaffCustomerCancelledOrderAndInvalidLines() {
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(actor).roles(List.of("CUSTOMER")).build());
        error(() -> service.get(order),CommonErrorCode.ACCESS_DENIED);
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(actor).roles(List.of("SALES_STAFF")).build());
        error(() -> service.get(order),CommonErrorCode.ACCESS_DENIED);
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(actor).roles(List.of("SHOP_OWNER")).build());
        error(() -> service.apply(order,request(UUID.randomUUID(),"11"),false),INVALID_REQUEST);
        var duplicate=new OrderStockReservationRequest(UUID.randomUUID(),"WH_MAIN",List.of(new Line(item,BigDecimal.ONE),new Line(item,BigDecimal.ONE)));
        error(() -> service.apply(order,duplicate,false),INVALID_REQUEST);
        var foreign=new OrderStockReservationRequest(UUID.randomUUID(),"WH_MAIN",List.of(new Line(UUID.randomUUID(),BigDecimal.ONE)));
        error(() -> service.apply(order,foreign,false),INVALID_REQUEST);
        jdbc.update("update orders set order_status='CANCELLED' where order_id=?",order);
        error(() -> service.get(order),ORDER_NOT_ELIGIBLE);
    }
    @Test void deferredDepositKeepsLegacyHoldsAndOnlyAllocatesAvailableUnits() {
        receipt("20","VERIFIED");
        jdbc.update("update orders set reserve_stock_after_deposit=true where order_id=?", order);
        jdbc.update("update inventories set reserved_qty=2 where inventory_id=?", inventory);
        var key = UUID.randomUUID();
        var held = service.apply(order,request(key,"10"),false);
        assertThat(held.reservations().getFirst().heldQty()).isEqualByComparingTo("4");
        assertThat(held.reservations().getFirst().waitingQty()).isEqualByComparingTo("6");
        assertThat(reserved()).isEqualByComparingTo("6");
        assertThat(jdbc.queryForObject("select sum(held_qty) from stock_reservations where inventory_id=?",
                BigDecimal.class,inventory)).isEqualByComparingTo("4");
        service.apply(order,request(key,"10.00"),false);
        service.apply(order,request(UUID.randomUUID(),"10"),true);
        assertThat(reserved()).isEqualByComparingTo("6");
        jdbc.update("update inventories set on_hand_qty=10 where inventory_id=?",inventory);
        var toppedUp = service.apply(order,request(UUID.randomUUID(),"10"),true);
        assertThat(toppedUp.reservations().getFirst().heldQty()).isEqualByComparingTo("8");
        assertThat(toppedUp.reservations().getFirst().waitingQty()).isEqualByComparingTo("2");
        assertThat(reserved()).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("select on_hand_qty from inventories where inventory_id=?",
                BigDecimal.class,inventory)).isEqualByComparingTo("10");
    }

    @Test void deferredDepositStillRejectsInventoryBelowRecordedHolds() {
        receipt("20","VERIFIED");
        jdbc.update("update orders set reserve_stock_after_deposit=true where order_id=?",order);
        service.apply(order,request(UUID.randomUUID(),"4"),false);
        jdbc.update("update inventories set reserved_qty=1 where inventory_id=?",inventory);
        error(() -> service.apply(order,request(UUID.randomUUID(),"4"),true), ReservationErrorCode.INCONSISTENT);
        assertThat(reserved()).isEqualByComparingTo("1");
        assertThat(service.get(order).reservations().getFirst().heldQty()).isEqualByComparingTo("4");
    }

    @Test void rejectsUnownedLegacyHoldsInsteadOfReservingTwice() {
        receipt("20","VERIFIED");
        jdbc.update("update inventories set reserved_qty=2 where inventory_id=?",inventory);
        error(() -> service.apply(order,request(UUID.randomUUID(),"10"),false),ReservationErrorCode.INCONSISTENT);
        assertThat(reserved()).isEqualByComparingTo("2");
        assertThat(service.get(order).reservationRequested()).isFalse();
    }
    @Test void ledgerFailureRollsBackSelectionPaidProjectionAndAllStock() {
        receipt("20","VERIFIED");
        doThrow(new IllegalStateException("ledger unavailable")).when(ledger).record(any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(() -> service.apply(order,request(UUID.randomUUID(),"10"),false)).isInstanceOf(IllegalStateException.class);
        assertThat(reserved()).isZero();
        assertThat(service.get(order).reservationRequested()).isFalse();
        assertThat(jdbc.queryForObject("select paid_amount from orders where order_id=?",BigDecimal.class,order)).isZero();
    }
    @Test void laterStockChecksExcludeUnitsAlreadyHeld() {
        receipt("20","VERIFIED");
        service.apply(order,request(UUID.randomUUID(),"4"),false);
        assertThat(legacy.checkAvailability(variant,new BigDecimal("3"))).isFalse();
        assertThat(legacy.checkAvailability(variant,new BigDecimal("2"))).isTrue();
        assertThat(legacy.getStockSummary(variant).orElseThrow().availableQty()).isEqualByComparingTo("2");
    }
    @Test void verifiedDepositMarksOnlyThreeOfTenUnitsAsHeld() {
        jdbc.update("update inventories set on_hand_qty=10 where inventory_id=?",inventory);
        receipt("20","VERIFIED");
        var result=service.apply(order,request(UUID.randomUUID(),"3"),false);
        assertThat(result.payment().depositRequired()).isEqualByComparingTo("20");
        assertThat(result.reservations().getFirst().status()).isEqualTo("HELD");
        assertThat(result.reservations().getFirst().heldQty()).isEqualByComparingTo("3");
        assertThat(reservationApi.stockByVariant(variant).getFirst().availableQty()).isEqualByComparingTo("7");
        assertThat(legacy.checkAvailability(variant,new BigDecimal("7"))).isTrue();
        assertThat(legacy.checkAvailability(variant,new BigDecimal("8"))).isFalse();
    }
    @Test void emptyStockRemainsWaitingRatherThanHeld() {
        receipt("20","VERIFIED");
        jdbc.update("update inventories set on_hand_qty=0 where inventory_id=?",inventory);
        var result=service.apply(order,request(UUID.randomUUID(),"10"),false);
        assertThat(result.status()).isEqualTo("WAITING_STOCK");
        assertThat(result.reservations().getFirst().heldQty()).isZero();
        assertThat(result.reservations().getFirst().waitingQty()).isEqualByComparingTo("10");
        assertThat(legacy.checkAvailability(variant,BigDecimal.ONE)).isFalse();
    }
    @Test void failureOnSecondLineRollsBackTheFirstAllocation() {
        receipt("20","VERIFIED");
        UUID secondItem=UUID.randomUUID();
        jdbc.update("insert into order_items(order_item_id,order_id,variant_id,quantity,line_total) values(?,?,?,10,100)",secondItem,order,variant);
        var count=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            if(count.incrementAndGet()==2) throw new IllegalStateException("second line failure");
            return invocation.callRealMethod();
        }).when(ledger).record(any(),any(),any(),any(),any(),any(),any(),any());
        var command=new OrderStockReservationRequest(UUID.randomUUID(),"WH_MAIN",List.of(new Line(item,BigDecimal.TEN),new Line(secondItem,BigDecimal.TEN)));
        assertThatThrownBy(() -> service.apply(order,command,false)).isInstanceOf(IllegalStateException.class);
        assertThat(reserved()).isZero();
        assertThat(operations()).isZero();
        assertThat(service.get(order).reservationRequested()).isFalse();
    }
    @Test void concurrentRetriesOnlyAllocateOnce() throws Exception {
        receipt("20","VERIFIED");
        var request=request(UUID.randomUUID(),"10");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<OrderStockReservationView> task=() -> { start.await();return service.apply(order,request,false); };
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();
            assertThat(a.get(30,TimeUnit.SECONDS).reservations()).hasSize(1);
            assertThat(b.get(30,TimeUnit.SECONDS).reservations()).hasSize(1);
        }
        assertThat(reserved()).isEqualByComparingTo("6");
        assertThat(operations()).isEqualTo(1);
    }
}
