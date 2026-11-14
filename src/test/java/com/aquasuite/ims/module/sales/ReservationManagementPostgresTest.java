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
        com.aquasuite.ims.module.sales.internal.service.ReservationManagementService.class,
        com.aquasuite.ims.module.sales.internal.service.ReservationActorNames.class,
        com.aquasuite.ims.module.sales.internal.repository.ReservationReleaseStore.class,
        ReservationManagementPostgresTest.Json.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ReservationManagementPostgresTest extends PostgreSqlIntegrationTestSupport {
    @TestConfiguration static class Json {
        @Bean ObjectMapper json() { return new ObjectMapper().findAndRegisterModules(); }
    }
    @Autowired OrderStockReservationService service;
    @Autowired InventoryServiceImpl legacy;
    @Autowired com.aquasuite.ims.module.inventory.api.StockReservationApi reservationApi;
    @Autowired JdbcTemplate jdbc;
    @MockBean IamApi iam;
    @MockBean com.aquasuite.ims.module.catalog.api.CatalogApi catalog;
    @SpyBean StockReservationStore ledger;
    @SpyBean com.aquasuite.ims.module.sales.internal.repository.ReservationReleaseStore releaseResults;
    @Autowired com.aquasuite.ims.module.sales.internal.service.ReservationManagementService management;
    UUID order,item,variant,customer,actor,inventory;
    @BeforeEach void fixture() {
        reset(ledger,iam,releaseResults);
        order=UUID.randomUUID();item=UUID.randomUUID();variant=UUID.randomUUID();
        customer=UUID.randomUUID();actor=UUID.randomUUID();inventory=UUID.randomUUID();
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(actor).roles(List.of("SHOP_OWNER")).build());
        when(iam.getUserById(actor)).thenReturn(UserSummaryDto.builder().id(actor).roles(List.of("SHOP_OWNER")).build());
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
    private UUID approvedDecision(String retained) {
        jdbc.update("insert into users(id,username,email,password_hash,status) values(?,?,?,?, 'ACTIVE')",
                actor,"uc36_"+actor,"uc36_"+actor+"@test.invalid","{disabled-test-account}");
        when(iam.getUserById(actor)).thenReturn(UserSummaryDto.builder().id(actor).roles(List.of("SHOP_OWNER")).build());
        UUID decision=UUID.randomUUID();
        jdbc.update("""
                insert into order_modification_requests(modification_request_id,order_id,customer_id,request_type,
                request_status,customer_reason,requested_by,reviewed_by,reviewed_at)
                values(?,?,?,?,'APPROVED','Customer request',?,?,current_timestamp)
                """,decision,order,customer,retained==null?"FULL_CANCELLATION":"ITEM_CHANGE",actor,actor);
        if(retained!=null) jdbc.update("""
                insert into order_modification_request_items(modification_request_item_id,modification_request_id,
                order_item_id,original_variant_id,change_action,original_quantity,requested_quantity,unit_price_snapshot)
                values(?,?,?,?,'REDUCE',10,?,10)
                """,UUID.randomUUID(),decision,item,variant,new BigDecimal(retained));
        return decision;
    }
    @Test void uc36ReleasesApprovedPartialQuantityAndReplaysWithoutChangingPhysicalStock() {
        receipt("20","VERIFIED");
        service.apply(order,request(UUID.randomUUID(),"10"),false);
        UUID decision=approvedDecision("2");
        var result=management.release(order,decision);
        assertThat(result.lines().getFirst().releasedQty()).isEqualByComparingTo("4");
        assertThat(reserved()).isEqualByComparingTo("2");
        assertThat(management.release(order,decision)).isEqualTo(result);
        assertThat(operations()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select on_hand_qty from inventories where inventory_id=?",BigDecimal.class,inventory)).isEqualByComparingTo("6");
        assertThat(management.history(order).decisions().getFirst().releaseStatus()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select to_regclass('public.sales_reservation_releases')::text",String.class)).isNull();
        assertThat(jdbc.queryForObject("select payload from sales_reservation_operations where request_id=?",String.class,decision))
                .startsWith("UC36_RELEASE:");
        error(() -> service.apply(order,request(UUID.randomUUID(),"10"),true),RESERVATION_SELECTION_CONFLICT);
    }
    @Test void uc36RollsBackStockAndLedgerWhenResultPersistenceFails() {
        receipt("20","VERIFIED");service.apply(order,request(UUID.randomUUID(),"10"),false);
        UUID decision=approvedDecision(null);
        doThrow(new IllegalStateException("Injected result failure")).when(releaseResults).save(any());
        assertThatThrownBy(() -> management.release(order,decision)).isInstanceOf(IllegalStateException.class);
        assertThat(reserved()).isEqualByComparingTo("6");
        assertThat(operations()).isEqualTo(1);
        assertThat(releaseResults.find(decision)).isEmpty();
        doCallRealMethod().when(releaseResults).save(any());
        assertThat(management.release(order,decision).lines().getFirst().releasedQty()).isEqualByComparingTo("6");
    }
    @Test void uc36ConcurrentExecutionReleasesOnlyOnce() throws Exception {
        receipt("20","VERIFIED");service.apply(order,request(UUID.randomUUID(),"10"),false);
        UUID decision=approvedDecision(null);
        var executor=Executors.newFixedThreadPool(2);
        try {
            var a=executor.submit(() -> management.release(order,decision));
            var b=executor.submit(() -> management.release(order,decision));
            assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo(b.get(20,TimeUnit.SECONDS));
            assertThat(reserved()).isZero();
            assertThat(operations()).isEqualTo(2);
        } finally { executor.shutdownNow(); }
    }
    @Test void uc36NeverAdoptsOrReleasesLegacyAggregateHolds() {
        receipt("20","VERIFIED");service.apply(order,request(UUID.randomUUID(),"4"),false);
        jdbc.update("update inventories set reserved_qty=reserved_qty+2 where inventory_id=?",inventory);
        UUID decision=approvedDecision(null);
        assertThat(management.release(order,decision).lines().getFirst().releasedQty()).isEqualByComparingTo("4");
        assertThat(reserved()).isEqualByComparingTo("2");
    }
    @Test void uc36ReductionCanCompleteWithZeroReleaseAndBlocksRefillingCancelledSelection() {
        receipt("20","VERIFIED");service.apply(order,request(UUID.randomUUID(),"10"),false);
        UUID decision=approvedDecision("8");
        var result=management.release(order,decision);
        assertThat(result.lines().getFirst().releasedQty()).isZero();
        assertThat(reserved()).isEqualByComparingTo("6");
        assertThat(management.release(order,decision)).isEqualTo(result);
        error(() -> service.apply(order,request(UUID.randomUUID(),"10"),true),RESERVATION_SELECTION_CONFLICT);
    }
    @Test void migrationPreservesExistingReleaseResults() throws Exception {
        UUID decision=approvedDecision(null);
        var result=management.release(order,decision);
        assertThat(result.lines()).isEmpty();
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.ClassPathResource("db/migration/V35__record_approved_reservation_releases.sql"));
            return null;
        });
        jdbc.update("delete from sales_reservation_operations where request_id=?",decision);
        jdbc.update("insert into sales_reservation_releases(decision_id,order_id,executed_by,executed_at,result) values(?,?,?,?,?::jsonb)",
                decision,order,actor,java.sql.Timestamp.from(result.executedAt()),new ObjectMapper().findAndRegisterModules().writeValueAsString(result));
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.ClassPathResource("db/migration/V38__reuse_sales_reservation_operations_for_releases.sql"));
            return null;
        });
        assertThat(releaseResults.find(decision)).contains(result);
        assertThat(management.release(order,decision)).isEqualTo(result);
        assertThat(jdbc.queryForObject("select to_regclass('public.sales_reservation_releases')::text",String.class)).isNull();
    }
    @Test void pendingCancellationBlocksAllocationWithoutChangingOtherOrders() {
        receipt("20","VERIFIED");
        UUID decision=approvedDecision(null);
        jdbc.update("update order_modification_requests set request_status='PENDING_CANCEL' where modification_request_id=?",decision);
        error(() -> service.apply(order,request(UUID.randomUUID(),"10"),false),RESERVATION_SELECTION_CONFLICT);
        assertThat(reserved()).isZero();
        jdbc.update("update order_modification_requests set request_status='REJECTED' where modification_request_id=?",decision);
        assertThat(service.apply(order,request(UUID.randomUUID(),"10"),false).reservations().getFirst().heldQty()).isEqualByComparingTo("6");
    }
    @Test void addressChangeDoesNotBlockNormalAllocation() {
        receipt("20","VERIFIED");
        UUID decision=approvedDecision(null);
        jdbc.update("update order_modification_requests set request_type='ADDRESS_CHANGE' where modification_request_id=?",decision);
        assertThat(service.apply(order,request(UUID.randomUUID(),"10"),false).reservations().getFirst().heldQty()).isEqualByComparingTo("6");
    }
}
