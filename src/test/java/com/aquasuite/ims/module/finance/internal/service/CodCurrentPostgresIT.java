package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.internal.service.impl.CodService;
import com.aquasuite.ims.module.finance.internal.payload.CodPayload.*;
import com.aquasuite.ims.module.finance.internal.payload.request.RecordOrderPaymentRequest;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import com.aquasuite.ims.module.finance.internal.payload.CodPayload.Collection;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class CodCurrentPostgresIT extends PostgreSqlIntegrationTestSupport {
    @Autowired CodService service;
    @Autowired OrderPaymentService payments;
    @Autowired PaymentVerificationService verifications;
    @Autowired JdbcTemplate jdbc;
    @MockBean IamApi iam;
    UUID shipper,accountant,customer,order,delivery;
    @BeforeEach void fixtures() {
        shipper=user();accountant=user();customer=UUID.randomUUID();order=UUID.randomUUID();delivery=UUID.randomUUID();
        jdbc.update("INSERT INTO customers(customer_id,customer_code,customer_name,phone) VALUES (?,?,'COD test',?)",customer,"COD-"+customer,"09"+System.nanoTime());
        jdbc.update("INSERT INTO orders(order_id,order_code,customer_id,order_channel,order_status,payment_status,order_date,total_amount) VALUES (?,?,?,'ONLINE','PROCESSING','UNPAID',now(),100)",order,"COD-"+order,customer);
        jdbc.update("INSERT INTO deliveries(delivery_id,delivery_code,order_id,warehouse_code,shipper_id,delivery_status,recipient_name,recipient_phone,delivery_address,cod_required,cod_amount,created_by) VALUES (?,?,?,'MAIN',?,'DELIVERED','Customer','0900000000','Test',true,100,?)",delivery,"COD-"+delivery,order,shipper,accountant);
    }
    UUID user() {
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO users(id,username,email,password_hash,status) VALUES (?,?,?,'{disabled-test-account}','ACTIVE')",id,"COD-"+id,id+"@example.invalid");return id;
    }
    void as(UUID id,String role) {
        when(iam.getCurrentUserId()).thenReturn(id);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,"unused",List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    Collection declare() {
        as(shipper,"SHIPPER");
        return service.claim(new Claim(delivery,new BigDecimal("100"),OffsetDateTime.now(),null,UUID.randomUUID()));
    }
    @AfterEach void clear(){
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM transactions WHERE order_id=?",order);
        jdbc.update("DELETE FROM deliveries WHERE order_id=?",order);
        jdbc.update("DELETE FROM orders WHERE order_id=?",order);
        jdbc.update("DELETE FROM customers WHERE customer_id=?",customer);
        jdbc.update("DELETE FROM users WHERE id IN (?,?)",shipper,accountant);
    }
    @Test void freshSchemaContainsNoCodTables() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('cod_collections','cod_remittances','cod_remittance_items')",Integer.class)).isZero();
    }
    @Test void pendingDeclarationOnlyPaysWhenCashIsActuallyReceivedAndReplayIsSafe() {
        var c=declare();
        assertThat(c.status()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT payment_status FROM orders WHERE order_id=?",String.class,order)).isEqualTo("UNPAID");
        as(accountant,"SHOP_OWNER");
        service.review(c.collectionId(),new Review(0,null),true);
        service.review(c.collectionId(),new Review(0,null),true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE order_id=?",Integer.class,order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM transactions WHERE transaction_id=?",String.class,c.collectionId())).isEqualTo("VERIFIED");
        assertThat(jdbc.queryForObject("SELECT payment_status FROM orders WHERE order_id=?",String.class,order)).isEqualTo("PAID");
        assertThat(service.balance(order).shipperHeld()).isZero();
    }
    @Test void cashReceiptCannotCollectPendingCodAgain() {
        declare(); as(accountant,"ACCOUNTANT");
        assertThatThrownBy(()->payments.record(order,new RecordOrderPaymentRequest(UUID.randomUUID(),BigDecimal.ONE,RecordOrderPaymentRequest.Method.CASH,null,null)))
                .isInstanceOf(com.aquasuite.ims.share.exception.AppException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE order_id=?",Integer.class,order)).isEqualTo(1);
    }
    @Test void bankVerificationCannotCollectPendingCodAgain() {
        as(accountant,"ACCOUNTANT");
        var bank=payments.record(order,new RecordOrderPaymentRequest(UUID.randomUUID(),new BigDecimal("100"),RecordOrderPaymentRequest.Method.BANK_TRANSFER,null,null));
        declare(); as(accountant,"ACCOUNTANT");
        assertThatThrownBy(()->verifications.verify(bank.transactionId())).isInstanceOf(com.aquasuite.ims.share.exception.AppException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM transactions WHERE transaction_id=?",String.class,bank.transactionId())).isEqualTo("PENDING");
    }
    @Test void rejectionReleasesBalanceWithoutPayingOrder() {
        var c=declare(); as(accountant,"ACCOUNTANT");
        service.review(c.collectionId(),new Review(0,"Not received"),false);
        assertThat(service.balance(order).shipperHeld()).isZero();
        assertThat(service.balance(order).customerCollectible()).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT payment_status FROM orders WHERE order_id=?",String.class,order)).isEqualTo("UNPAID");
    }
    @Test void pendingCodBlocksOrderTotalChange() {
        declare();
        assertThatThrownBy(()->jdbc.update("UPDATE orders SET total_amount=90 WHERE order_id=?",order))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void ordinaryBalanceCanBePaidAlongsidePendingCod() {
        jdbc.update("UPDATE orders SET total_amount=200 WHERE order_id=?",order);
        var c=declare(); as(accountant,"ACCOUNTANT");
        payments.record(order,new RecordOrderPaymentRequest(UUID.randomUUID(),new BigDecimal("100"),RecordOrderPaymentRequest.Method.CASH,null,null));
        service.review(c.collectionId(),new Review(0,null),true);
        assertThat(jdbc.queryForObject("SELECT payment_status FROM orders WHERE order_id=?",String.class,order)).isEqualTo("PAID");
    }
    @Test void concurrentDeclarationsCannotOverCollect() throws Exception {
        as(shipper,"SHIPPER");
        var gate=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> operation=()->{
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(shipper,"unused",List.of(new SimpleGrantedAuthority("ROLE_SHIPPER"))));
                try { gate.await(10,TimeUnit.SECONDS);
                    service.claim(new Claim(delivery,new BigDecimal("100"),OffsetDateTime.now(),null,UUID.randomUUID())); return true;
                } catch(com.aquasuite.ims.share.exception.AppException ex) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            };
            var a=pool.submit(operation);var b=pool.submit(operation);
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE order_id=?",Integer.class,order)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
}
