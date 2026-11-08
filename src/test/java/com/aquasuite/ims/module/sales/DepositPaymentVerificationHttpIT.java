package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the existing UC23 -> UC70 -> UC29 HTTP contracts against real PostgreSQL. */
@SpringBootTest(properties="aquasuite.reservations.retry-enabled=false")
@AutoConfigureMockMvc
class DepositPaymentVerificationHttpIT extends PostgreSqlIntegrationTestSupport {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockBean IamApi iam;
    @SpyBean com.aquasuite.ims.module.inventory.internal.service.impl.StockReservationService stock;
    @SpyBean com.aquasuite.ims.module.sales.internal.service.ReservationAllocationRunner runner;
    @Autowired com.aquasuite.ims.module.sales.internal.repository.ReservationAllocationStore jobs;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired com.aquasuite.ims.module.finance.internal.service.PaymentVerificationService verifications;
    UUID order, item, actor, inventory, customer, variant;

    @BeforeEach
    void seed() {
        order = UUID.randomUUID(); item = UUID.randomUUID(); actor = UUID.randomUUID();
        inventory = UUID.randomUUID();
        customer = UUID.randomUUID();
        variant = UUID.randomUUID();
        UUID category = UUID.randomUUID(), product = UUID.randomUUID();
        when(iam.getCurrentUserId()).thenReturn(actor);
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(actor)
                .roles(List.of("SALES_STAFF")).build());
        jdbc.update("INSERT INTO users(id,username,email,password_hash,status) VALUES (?,?,?,?,'ACTIVE')",
                actor, "deposit-" + actor, actor + "@example.invalid", "{disabled-test-account}");
        jdbc.update("INSERT INTO customers(customer_id,customer_code,customer_name,phone) VALUES (?,?,'Test',?)",
                customer, "C-" + customer, String.format("09%08d",Math.floorMod(customer.hashCode(),100000000)));
        jdbc.update("INSERT INTO categories(category_id,category_code,category_name) VALUES (?,?,?)",
                category, "C_" + category.toString().replace("-", "").toUpperCase(), "Deposit " + category);
        jdbc.update("INSERT INTO products(product_id,product_code,category_id,product_name) VALUES (?,?,?,'Test')",
                product, "P-" + product, category);
        jdbc.update("INSERT INTO product_variants(variant_id,product_id,sku,variant_name,unit) VALUES (?,?,?,'Test','piece')",
                variant, product, "SKU-" + variant);
        // Finance supplies the agreed deposit; selected stock value must not replace it.
        jdbc.update("""
                INSERT INTO orders(order_id,order_code,customer_id,sales_staff_id,order_channel,
                    order_status,payment_status,order_date,total_amount,deposit_amount)
                VALUES (?,?,?,?,'COUNTER','PROCESSING','UNPAID',now(),100,30)
                """, order, "O-" + order, customer, actor);
        jdbc.update("INSERT INTO order_items(order_item_id,order_id,variant_id,quantity,line_total) VALUES (?,?,?,10,100)",
                item, order, variant);
        jdbc.update("""
                INSERT INTO inventories(inventory_id,variant_id,warehouse_code,on_hand_qty,reserved_qty,minimum_qty,updated_at)
                VALUES (?,?,'WH_MAIN',6,0,0,now())
                """, inventory, variant);
    }

    @Test
    void deferredVerifiedDepositPreservesOtherWorkflowHolds() throws Exception {
        jdbc.update("update orders set reserve_stock_after_deposit=true where order_id=?",order);
        jdbc.update("update inventories set reserved_qty=2 where inventory_id=?",inventory);
        reserve("requests",UUID.randomUUID(),4);
        assertThat(held()).isEqualByComparingTo("2");
        UUID payment = transfer(30);
        decide(payment,true);
        var result = reservation();
        assertThat(result.path("depositStatus").asText()).isEqualTo("SATISFIED");
        assertThat(result.path("status").asText()).isEqualTo("HELD");
        assertThat(result.path("reservations").get(0).path("heldQty").decimalValue()).isEqualByComparingTo("4");
        assertThat(held()).isEqualByComparingTo("6");
        reserve("synchronize",UUID.randomUUID(),4);
        assertThat(held()).isEqualByComparingTo("6");
        assertThat(jdbc.queryForObject("select on_hand_qty from inventories where inventory_id=?",
                BigDecimal.class,inventory)).isEqualByComparingTo("6");
        assertThat(jdbc.queryForObject("select status from transactions where transaction_id=?",
                String.class,payment)).isEqualTo("VERIFIED");
    }

    @Test
    void orderListOptInFilterBindsWithoutChangingOrdinaryListOrPayments() throws Exception {
        String path = "/api/v1/sales/orders";
        String code = "O-" + order;
        var ordinary = http.perform(get(path).param("keyword",code)
                .with(user("sales").roles("SALES_STAFF"))).andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(ordinary.getResponse().getContentAsString()).at("/data/totalElements").asInt()).isEqualTo(1);
        assertThat(json.readTree(ordinary.getResponse().getContentAsString()).at("/data/content/0").has("payment")).isFalse();
        var excluded = http.perform(get(path).param("keyword",code).param("depositReservationOnly","true")
                .with(user("sales").roles("SALES_STAFF"))).andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(excluded.getResponse().getContentAsString()).at("/data/totalElements").asInt()).isEqualTo(1);
        assertThat(managementRow(code).path("depositStatus").asText()).isEqualTo("AWAITING_DEPOSIT");
        jdbc.update("update orders set deposit_percent=20,deposit_amount=20 where order_id=?",order);
        UUID first = transfer(10), second = transfer(10);
        assertThat(managementCount(code)).isEqualTo(1);
        assertThat(managementRow(code).path("payment").path("verifiedPaid").decimalValue()).isZero();
        decide(first,true);
        assertThat(managementRow(code).path("depositStatus").asText()).isEqualTo("AWAITING_DEPOSIT");
        assertThat(managementRow(code).path("payment").path("verifiedPaid").decimalValue()).isEqualByComparingTo("10");
        decide(second,true);
        assertThat(managementRow(code).path("depositStatus").asText()).isEqualTo("SATISFIED");
        UUID unpaid = UUID.randomUUID();
        jdbc.update("""
                insert into orders(order_id,order_code,customer_id,sales_staff_id,order_channel,
                    order_status,payment_status,order_date,total_amount,deposit_percent,deposit_amount)
                values(?,?,?,?,'COUNTER','PROCESSING','UNPAID',now(),100,30,30)
                """,unpaid,code+"-unpaid",customer,actor);
        assertThat(managementCount(code)).isEqualTo(2);
        var included = http.perform(get(path).param("keyword",code).param("depositReservationOnly","true")
                .with(user("sales").roles("SALES_STAFF"))).andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(included.getResponse().getContentAsString()).at("/data/totalElements").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select reserve_stock_after_deposit from orders where order_id=?",Boolean.class,order)).isFalse();
        assertThat(jdbc.queryForObject("select reserved_qty from inventories where inventory_id=?",BigDecimal.class,inventory)).isEqualByComparingTo("0");
        jdbc.update("update orders set deposit_percent=30 where order_id=?",order);
        assertThat(managementCount(code)).isEqualTo(2);
        jdbc.update("update orders set deposit_percent=20,order_status='CANCELLED' where order_id=?",order);
        assertThat(managementCount(code)).isEqualTo(1);
        jdbc.update("update orders set deposit_amount=0,deposit_percent=null where order_id=?",unpaid);
        assertThat(managementCount(code)).isZero();
    }

    private JsonNode managementRow(String keyword) throws Exception {
        var response = http.perform(get("/api/v1/sales/orders").param("keyword",keyword)
                .param("depositReservationOnly","true")
                .with(user("sales").roles("SALES_STAFF"))).andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).at("/data/content/0");
    }

    private int managementCount(String keyword) throws Exception {
        var response = http.perform(get("/api/v1/sales/orders").param("keyword",keyword)
                .param("depositReservationOnly","true").param("size","1")
                .with(user("sales").roles("SALES_STAFF"))).andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).at("/data/totalElements").asInt();
    }

    private String reservationUrl() {
        return "/api/v1/sales/orders/" + order + "/stock-reservations";
    }

    private JsonNode reserve(String action, UUID key, int quantity) throws Exception {
        return salesPost(reservationUrl() + "/" + action, Map.of("requestId", key,
                "warehouseCode", "WH_MAIN", "lines", List.of(Map.of("orderItemId", item, "quantity", quantity))));
    }

    private JsonNode salesPost(String url, Object body) throws Exception {
        return json.readTree(http.perform(post(url).with(user("sales").roles("SALES_STAFF"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }

    private UUID transfer(int amount) throws Exception {
        var response = salesPost("/api/v1/finance/orders/" + order + "/payments", Map.of(
                "idempotencyKey", UUID.randomUUID(), "amount", amount, "paymentMethod", "BANK_TRANSFER"));
        assertThat(response.path("status").asText()).isEqualTo("PENDING");
        return UUID.fromString(response.path("transactionId").asText());
    }

    private void decide(UUID payment, boolean verify) throws Exception {
        http.perform(post("/api/v1/finance/payment-verifications/" + payment + (verify ? "/verify" : "/reject"))
                .with(user("accountant").roles("ACCOUNTANT")).contentType(MediaType.APPLICATION_JSON)
                .content(verify ? "{}" : "{\"reason\":\"Transfer not received\"}"))
                .andExpect(status().isOk());
    }

    private BigDecimal held() {
        return jdbc.queryForObject("SELECT reserved_qty FROM inventories WHERE inventory_id=?", BigDecimal.class, inventory);
    }

    @Test
    void convertsTwentyPercentQuotationAndAutomaticallyHoldsMainWarehouseAfterVerifiedFunding() throws Exception {
        UUID quotation = UUID.randomUUID();
        jdbc.update("""
                insert into quotations(quotation_id,quotation_code,customer_id,project_name,quotation_date,
                    valid_until,created_by,status,deposit_percent,vat_rate,shipping_fee,total_amount)
                values(?,?,?,'Deposit project',current_date,current_date+30,?,'ACCEPTED',20,0,0,100)
                """,quotation,"Q-"+quotation,customer,actor);
        jdbc.update("""
                insert into quotation_items(quotation_item_id,quotation_id,variant_id,quantity,unit_price,sku,unit)
                values(?,?,?,10,10,?,'piece')
                """,UUID.randomUUID(),quotation,variant,"SKU-"+variant);
        var converted = http.perform(post("/api/v1/sales/orders/from-quotation/"+quotation)
                .with(user("sales").roles("SALES_STAFF"))).andExpect(status().isCreated()).andReturn();
        var data = json.readTree(converted.getResponse().getContentAsString()).path("data");
        order = UUID.fromString(data.path("orderId").asText());
        assertThat(data.path("depositPercent").decimalValue()).isEqualByComparingTo("20");
        assertThat(data.path("depositAmount").decimalValue()).isEqualByComparingTo("20");
        assertThat(jdbc.queryForObject("select reserve_stock_after_deposit from orders where order_id=?",Boolean.class,order)).isTrue();
        assertThat(reservation().path("warehouseCode").asText()).isEqualTo("WH_MAIN");
        assertThat(reservation().path("lines").get(0).path("quantity").decimalValue()).isEqualByComparingTo("10");
        assertThat(held()).isZero();
        UUID first = transfer(10), second = transfer(10);
        assertThat(held()).isZero();
        decide(first,true);
        assertThat(held()).isZero();
        decide(second,true);
        assertThat(held()).isEqualByComparingTo("6");
        assertThat(reservation().path("status").asText()).isEqualTo("PARTIALLY_HELD");
        assertThat(reservation().path("payment").path("depositSatisfied").asBoolean()).isTrue();
    }

    private JsonNode reservation() throws Exception {
        return json.readTree(http.perform(get(reservationUrl()).with(user("sales").roles("SALES_STAFF")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }

    @Test
    void usesFinanceAgreedDepositForSelectedGoods() throws Exception {
        var selected = reserve("requests", UUID.randomUUID(), 4);
        assertThat(selected.path("reservationValue").decimalValue()).isEqualByComparingTo("40");
        assertThat(selected.path("payment").path("depositRequired").decimalValue()).isEqualByComparingTo("30");
        assertThat(selected.path("status").asText()).isEqualTo("AWAITING_DEPOSIT");
        UUID payment = transfer(30);
        assertThat(reservation().path("payment").path("pendingAmount").decimalValue()).isEqualByComparingTo("30");
        assertThat(reservation().path("depositStatus").asText()).isEqualTo("PENDING_VERIFICATION");
        assertThat(reserve("synchronize", UUID.randomUUID(), 4).path("payment").path("depositSatisfied").asBoolean()).isFalse();
        assertThat(held()).isZero();

        decide(payment, true);
        assertThat(held()).isEqualByComparingTo("4"); // No synchronize call is needed after verification.
        assertThat(reservation().path("depositStatus").asText()).isEqualTo("SATISFIED");
        assertThat(reservation().path("allocationStatus").asText()).isEqualTo("COMPLETED");
        assertThat(reservation().path("lineStatuses").get(0).path("status").asText()).isEqualTo("HELD");
        var key = UUID.randomUUID();
        var result = reserve("synchronize", key, 4);
        assertThat(result.path("payment").path("verifiedPaid").decimalValue()).isEqualByComparingTo("30");
        assertThat(result.path("payment").path("depositSatisfied").asBoolean()).isTrue();
        assertThat(result.path("status").asText()).isEqualTo("HELD");
        assertThat(held()).isEqualByComparingTo("4");
        reserve("synchronize", key, 4);
        assertThat(held()).isEqualByComparingTo("4");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE order_id=?", Integer.class, order)).isEqualTo(1);
    }

    @Test
    void rejectedDepositNeverReservesStock() throws Exception {
        reserve("requests", UUID.randomUUID(), 4);
        UUID payment = transfer(30);
        decide(payment, false);
        var result = reserve("synchronize", UUID.randomUUID(), 4);
        assertThat(result.path("payment").path("verifiedPaid").decimalValue()).isZero();
        assertThat(result.path("status").asText()).isEqualTo("AWAITING_DEPOSIT");
        assertThat(held()).isZero();
    }

    @Test
    void severalVerifiedPaymentsMustReachAgreedDepositBeforeHolding() throws Exception {
        reserve("requests", UUID.randomUUID(), 10);
        UUID first = transfer(20), second = transfer(10);
        decide(first, true);
        assertThat(reserve("synchronize", UUID.randomUUID(), 10).path("status").asText()).isEqualTo("AWAITING_DEPOSIT");
        assertThat(held()).isZero();
        decide(second, true);
        assertThat(held()).isEqualByComparingTo("6"); // Automatic partial allocation.
        var result = reserve("synchronize", UUID.randomUUID(), 10);
        assertThat(result.path("status").asText()).isEqualTo("PARTIALLY_HELD");
        assertThat(held()).isEqualByComparingTo("6");
        assertThat(result.path("reservations").get(0).path("waitingQty").decimalValue()).isEqualByComparingTo("4");
    }

    @Test
    void salesCannotVerifyTheirOwnDeclaredTransfer() throws Exception {
        UUID payment = transfer(20);
        http.perform(post("/api/v1/finance/payment-verifications/" + payment + "/verify")
                .with(user("sales").roles("SALES_STAFF"))).andExpect(status().isForbidden());
        var response = json.readTree(http.perform(get(reservationUrl()).with(user("sales").roles("SALES_STAFF")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(response.path("payment").path("verifiedPaid").decimalValue()).isZero();
        assertThat(held()).isZero();
    }

    @Test
    void allocationFailureKeepsPaymentVerifiedAndRetriesTheSameDurableJob() throws Exception {
        reserve("requests", UUID.randomUUID(), 4);
        UUID payment = transfer(30);
        doThrow(new IllegalStateException("Transient stock failure")).doCallRealMethod()
                .when(stock).reserveForDeposit(any());
        decide(payment,true);
        assertThat(jdbc.queryForObject("SELECT status FROM transactions WHERE transaction_id=?",String.class,payment)).isEqualTo("VERIFIED");
        assertThat(held()).isZero();
        var failed=reservation();
        assertThat(failed.path("depositStatus").asText()).isEqualTo("SATISFIED");
        assertThat(failed.path("allocationStatus").asText()).isEqualTo("FAILED");
        assertThat(failed.path("allocationErrorCode").asText()).isEqualTo("SALES_DEPOSIT_006");
        UUID id=UUID.fromString(failed.path("allocationId").asText());
        salesPost(reservationUrl()+"/allocations/"+id+"/retry",Map.of());
        salesPost(reservationUrl()+"/allocations/"+id+"/retry",Map.of());
        assertThat(held()).isEqualByComparingTo("4");
        assertThat(reservation().path("allocationStatus").asText()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE order_id=?",Integer.class,order)).isEqualTo(1);
    }

    @Test
    void durablePendingWorkRecoversWithoutRequestAuthentication() throws Exception {
        reserve("requests",UUID.randomUUID(),4);
        UUID payment=transfer(30);
        doNothing().when(runner).attempt(any()); // Simulate loss of the immediate after-commit wakeup.
        decide(payment,true);
        UUID id=UUID.fromString(reservation().path("allocationId").asText());
        assertThat(jobs.find(id).orElseThrow().status()).isEqualTo("PENDING");
        assertThat(held()).isZero();
        doCallRealMethod().when(runner).attempt(any());
        var selectedActor=iam.getCurrentUser();
        when(iam.getCurrentUser()).thenReturn(null);
        assertThat(jobs.due()).contains(id);
        runner.attempt(id);
        runner.attempt(id);
        assertThat(held()).isEqualByComparingTo("4");
        assertThat(jobs.find(id).orElseThrow().status()).isEqualTo("DONE");
        when(iam.getCurrentUser()).thenReturn(selectedActor);
    }

    @Test
    void paymentRollbackCannotLeaveAReservationOrAllocationJob() throws Exception {
        reserve("requests",UUID.randomUUID(),4);
        UUID payment=transfer(30);
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            verifications.verify(payment);
            tx.setRollbackOnly();
        });
        assertThat(held()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM transactions WHERE transaction_id=?",String.class,payment)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_reservation_allocations WHERE order_id=?",Integer.class,order)).isZero();
    }

    @Test
    void verifiedCashAlsoAutomaticallyReservesAndIdempotentRetryDoesNotDuplicate() throws Exception {
        reserve("requests",UUID.randomUUID(),4);
        var body=Map.of("idempotencyKey",UUID.randomUUID(),"amount",30,"paymentMethod","CASH");
        salesPost("/api/v1/finance/orders/"+order+"/payments",body);
        salesPost("/api/v1/finance/orders/"+order+"/payments",body);
        assertThat(held()).isEqualByComparingTo("4");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_reservation_allocations WHERE order_id=?",Integer.class,order)).isEqualTo(1);
    }

    @Test
    void ordinaryVerifiedPaymentWithoutSelectionDoesNotReserveAnything() throws Exception {
        UUID payment=transfer(20);
        decide(payment,true);
        assertThat(held()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_reservation_allocations WHERE order_id=?",Integer.class,order)).isZero();
    }

    @Test
    void orderWithoutAgreedDepositDoesNotInventTwentyPercentThreshold() throws Exception {
        jdbc.update("UPDATE product_variants SET selling_price=10 WHERE variant_id=?",variant);
        var body=Map.of("customerId",customer,"reserveStockAfterDeposit",true,"paymentMethod","CASH",
                "items",List.of(Map.of("variantId",variant,"quantity",10)));
        var created=json.readTree(http.perform(post("/api/v1/sales/orders")
                .with(user("sales").roles("SALES_STAFF")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("data");
        order=UUID.fromString(created.path("orderId").asText());
        item=UUID.fromString(created.path("items").get(0).path("orderItemId").asText());
        assertThat(held()).isZero();
        assertThat(created.path("totalAmount").decimalValue()).isEqualByComparingTo("100");
        reserve("requests",UUID.randomUUID(),10);
        decide(transfer(20),true);
        assertThat(held()).isZero();
        assertThat(reservation().path("payment").path("depositSatisfied").asBoolean()).isFalse();
        assertThat(reservation().path("status").asText()).isEqualTo("AWAITING_DEPOSIT");
    }

    @Test
    void savedWorkDoesNotAllocateIfOrderIsCancelledBeforeRecovery() throws Exception {
        reserve("requests",UUID.randomUUID(),4);
        UUID payment=transfer(30);
        doNothing().when(runner).attempt(any());
        decide(payment,true);
        UUID id=UUID.fromString(reservation().path("allocationId").asText());
        jdbc.update("UPDATE orders SET order_status='CANCELLED' WHERE order_id=?",order);
        doCallRealMethod().when(runner).attempt(any());
        runner.attempt(id);
        assertThat(held()).isZero();
        assertThat(jobs.find(id).orElseThrow().status()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT status FROM transactions WHERE transaction_id=?",String.class,payment)).isEqualTo("VERIFIED");
    }

    @Test
    void concurrentRecoveryOfTheSamePersistedJobOnlyHoldsOnce() throws Exception {
        reserve("requests",UUID.randomUUID(),4);
        UUID payment=transfer(30);
        doNothing().when(runner).attempt(any());
        decide(payment,true);
        UUID id=UUID.fromString(reservation().path("allocationId").asText());
        doCallRealMethod().when(runner).attempt(any());
        try (var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Void> attempt=() -> { gate.await(); runner.attempt(id); return null; };
            var first=pool.submit(attempt);var second=pool.submit(attempt);gate.countDown();
            first.get(20,java.util.concurrent.TimeUnit.SECONDS);
            second.get(20,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(held()).isEqualByComparingTo("4");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_reservation_operations o JOIN stock_reservations r USING(reservation_id) WHERE r.source_id=?",
                Integer.class,order)).isEqualTo(1);
    }
}
