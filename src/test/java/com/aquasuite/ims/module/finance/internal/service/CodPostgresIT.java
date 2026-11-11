package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.internal.payload.CodPayload.*;
import com.aquasuite.ims.module.finance.internal.payload.request.DebtReminderRequest;
import com.aquasuite.ims.module.finance.internal.payload.request.RecordOrderPaymentRequest;
import com.aquasuite.ims.module.finance.internal.service.impl.CodService;
import com.aquasuite.ims.module.finance.internal.error.CodErrorCode;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class CodPostgresIT extends PostgreSqlIntegrationTestSupport {
    @Autowired CodService cod;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderPaymentService payments;
    @Autowired PaymentVerificationService verifications;
    @Autowired CustomerDebtService debts;
    @MockBean IamApi iam;
    UUID shipper, accountant, customer, order, delivery;

    @BeforeEach void fixtures() {
        shipper=user(); accountant=user(); customer=UUID.randomUUID(); order=UUID.randomUUID(); delivery=UUID.randomUUID();
        jdbc.update("INSERT INTO customers(customer_id,customer_code,customer_name,phone) VALUES (?,?,'COD test',?)",
                customer,"C-"+customer,"09"+System.nanoTime());
        jdbc.update("""
            INSERT INTO orders(order_id,order_code,customer_id,order_channel,order_status,payment_status,order_date,total_amount)
            VALUES (?,?,?,'ONLINE','PROCESSING','UNPAID',now(),22220000)
            """,order,"O-"+order,customer);
        jdbc.update("""
            INSERT INTO deliveries(delivery_id,delivery_code,order_id,warehouse_code,shipper_id,delivery_status,
            recipient_name,recipient_phone,delivery_address,cod_required,cod_amount,created_by)
            VALUES (?,?,?,'MAIN',?,'DELIVERED','Customer','0900000000','Test address',true,22220000,?)
            """,delivery,"D-"+delivery,order,shipper,accountant);
    }
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }
    private UUID user() {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,username,email,password_hash,status) VALUES (?,?,?,'{disabled-test-account}','ACTIVE')",
                id,"cod-"+id,id+"@example.invalid");
        return id;
    }
    private void as(UUID id,String role) {
        when(iam.getCurrentUserId()).thenReturn(id);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,"unused",
                List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    private Collection submit(String amount) throws Exception {
        as(shipper,"SHIPPER");
        return cod.claim(new Claim(delivery,new BigDecimal(amount),OffsetDateTime.now(),null,UUID.randomUUID()));
    }
    private Collection accept(String amount) throws Exception {
        var c=submit(amount);
        as(accountant,"ACCOUNTANT");
        return cod.review(c.collectionId(),new Review(c.version(),null),true);
    }
    private Remit remit(Collection c,String amount,UUID key) {
        return new Remit(key,shipper,new BigDecimal(amount),List.of(new Allocation(c.collectionId(),new BigDecimal(amount),c.version())),null);
    }
    private String status() {
        return jdbc.queryForObject("SELECT payment_status FROM orders WHERE order_id=?",String.class,order);
    }

    @Test void declarationWithoutPhotoDoesNotPayOrderAndPartialThenFullRemittancePaysExactlyOnce() throws Exception {
        var c=accept("22220000");
        assertThat(jdbc.queryForObject("SELECT proof_content IS NULL AND proof_type IS NULL FROM cod_collections WHERE collection_id=?",
                Boolean.class,c.collectionId())).isTrue();
        assertThat(status()).isEqualTo("UNPAID");
        assertThat(cod.balance(order).customerCollectible()).isZero();
        UUID key=UUID.randomUUID();
        var request=remit(c,"22000000",key);
        Receipt first=cod.remit(request);
        assertThat(cod.remit(request).remittanceId()).isEqualTo(first.remittanceId());
        assertThat(status()).isEqualTo("PARTIALLY_PAID");
        assertThat(cod.balance(order).shipperHeld()).isEqualByComparingTo("220000");
        assertThat(cod.balance(order).customerCollectible()).isZero();
        var updated=cod.detail(c.collectionId());
        cod.remit(remit(updated,"220000",UUID.randomUUID()));
        assertThat(status()).isEqualTo("PAID");
        assertThat(cod.balance(order).storeOutstanding()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE order_id=?",Integer.class,order)).isEqualTo(2);
        assertThat(cod.history(c.collectionId())).hasSize(2);
    }
    @Test void customerShortPaymentLeavesCustomerDebtNotShipperDebt() throws Exception {
        var c=accept("22000000");
        cod.remit(remit(c,"22000000",UUID.randomUUID()));
        assertThat(cod.balance(order).shipperHeld()).isZero();
        assertThat(cod.balance(order).customerCollectible()).isEqualByComparingTo("220000");
        assertThat(status()).isEqualTo("PARTIALLY_PAID");
    }
    @Test void acceptedCodBlocksCustomerDoublePaymentAndOutgoingDebtReminder() throws Exception {
        accept("22220000");
        assertThatThrownBy(()->payments.record(order,new RecordOrderPaymentRequest(UUID.randomUUID(),BigDecimal.ONE,
                RecordOrderPaymentRequest.Method.CASH,null,null))).isInstanceOf(AppException.class);
        assertThatThrownBy(()->debts.sendReminder(customer,new DebtReminderRequest(DebtReminderRequest.ReminderType.OVERDUE),accountant))
                .isInstanceOf(AppException.class).hasMessageContaining("COD");
        var detail=debts.detail(customer);
        assertThat(detail.summary().codReviewRequired()).isTrue();
        assertThat(detail.orders().getFirst().customerCollectibleAmount()).isZero();
        assertThat(detail.summary().debtStatus().name()).isEqualTo("COD_REVIEW");
    }
    @Test void orderAmountAndCancellationCannotChangeWithOpenCod() throws Exception {
        submit("100");
        assertThatThrownBy(()->jdbc.update("UPDATE orders SET total_amount=1 WHERE order_id=?",order))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE orders SET order_status='CANCELLED' WHERE order_id=?",order))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void wrongShipperCannotDeclareCollection() {
        var claim=new Claim(delivery,BigDecimal.TEN,OffsetDateTime.now(),null,UUID.randomUUID());
        as(accountant,"SHIPPER");
        assertThatThrownBy(()->cod.claim(claim)).isInstanceOf(AppException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cod_collections WHERE order_id=?",Integer.class,order)).isZero();
    }
    @Test void claimReplayAndChangedMetadataAreChecked() {
        as(shipper,"SHIPPER");
        var claim=new Claim(delivery,BigDecimal.TEN,OffsetDateTime.now(),null,UUID.randomUUID());
        var first=cod.claim(claim);
        assertThat(cod.claim(claim).collectionId()).isEqualTo(first.collectionId());
        var changed=new Claim(delivery,BigDecimal.ONE,claim.collectedAt(),null,claim.idempotencyKey());
        assertThatThrownBy(()->cod.claim(changed)).isInstanceOf(AppException.class);
    }
    @Test void rejectionRetainsDeclarationAndFreesClaimCapacity() throws Exception {
        var c=submit("22220000");
        as(accountant,"ACCOUNTANT");
        cod.review(c.collectionId(),new Review(c.version(),"Số khai thu cần kiểm tra lại"),false);
        assertThat(cod.detail(c.collectionId()).rejectReason()).isEqualTo("Số khai thu cần kiểm tra lại");
        assertThat(submit("22220000").reviewStatus()).isEqualTo("SUBMITTED");
    }
    @Test void otherShipperCannotReadOrListAnotherShippersClaims() throws Exception {
        var c=submit("100");
        as(accountant,"SHIPPER");
        assertThatThrownBy(()->cod.detail(c.collectionId())).isInstanceOf(AppException.class);
        assertThatThrownBy(()->cod.list(shipper,null,null,0,20)).isInstanceOf(AppException.class);
        assertThat(cod.list(null,null,null,0,20).items()).isEmpty();
    }
    @Test void twoAccountantsCannotReceiveTheSameBalanceTwice() throws Exception {
        var c=accept("22220000");
        var gate=new CyclicBarrier(2);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Callable<String> attempt=()-> {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(accountant,"unused",
                        List.of(new SimpleGrantedAuthority("ROLE_ACCOUNTANT"))));
                try {
                    gate.await(10,TimeUnit.SECONDS);
                    cod.remit(remit(c,"22220000",UUID.randomUUID()));
                    return "OK";
                } catch (AppException e) { return e.getErrorCode().getCode(); }
                finally { SecurityContextHolder.clearContext(); }
            };
            var a=pool.submit(attempt); var b=pool.submit(attempt);
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK","COD_002");
            assertThat(cod.balance(order).shipperHeld()).isZero();
        } finally { pool.shutdownNow(); }
    }
    @Test void failureUpdatingSalesRollsBackReceiptAndAllocations() throws Exception {
        var c=accept("22220000");
        jdbc.execute("ALTER TABLE orders ADD CONSTRAINT cod_test_failure CHECK (order_id <> '"+order+"'::uuid OR payment_status='UNPAID')");
        try {
            assertThatThrownBy(()->cod.remit(remit(c,"22220000",UUID.randomUUID()))).isInstanceOf(RuntimeException.class);
            assertThat(cod.history(c.collectionId())).isEmpty();
            assertThat(cod.balance(order).shipperHeld()).isEqualByComparingTo("22220000");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE order_id=?",Integer.class,order)).isZero();
        } finally { jdbc.execute("ALTER TABLE orders DROP CONSTRAINT cod_test_failure"); }
    }
    @Test void pendingTransferCannotBeVerifiedAfterTheSameMoneyIsAcceptedAsCod() throws Exception {
        as(accountant,"ACCOUNTANT");
        UUID transfer=payments.record(order,new RecordOrderPaymentRequest(UUID.randomUUID(),new BigDecimal("22220000"),
                RecordOrderPaymentRequest.Method.BANK_TRANSFER,"COD-TEST-TRANSFER",null)).transactionId();
        accept("22220000");
        assertThatThrownBy(()->verifications.verify(transfer)).isInstanceOf(AppException.class);
        assertThat(status()).isEqualTo("UNPAID");
        assertThat(cod.balance(order).shipperHeld()).isEqualByComparingTo("22220000");
    }
    @Test void multipleCollectionsInOneRemittanceCreateLinkedTransactionsOnce() throws Exception {
        var first=accept("10000000");
        var second=accept("12220000");
        var request=new Remit(UUID.randomUUID(),shipper,new BigDecimal("22220000"),List.of(
                new Allocation(first.collectionId(),first.amount(),first.version()),
                new Allocation(second.collectionId(),second.amount(),second.version())),"Hai khoản");
        var receipt=cod.remit(request);
        assertThat(receipt.items()).hasSize(2);
        assertThat(receipt.items().stream().map(ReceiptItem::transactionId).distinct().count()).isEqualTo(2);
        assertThat(cod.remit(request).remittanceId()).isEqualTo(receipt.remittanceId());
        assertThat(status()).isEqualTo("PAID");
    }
    @Test void selfReviewByMultiRoleAccountAndOverRemittanceAreForbidden() throws Exception {
        var c=submit("100");
        as(shipper,"ACCOUNTANT");
        assertThatThrownBy(()->cod.review(c.collectionId(),new Review(0,null),true)).isInstanceOf(AppException.class);
        as(accountant,"ACCOUNTANT");
        Collection accepted=cod.review(c.collectionId(),new Review(0,null),true);
        assertThatThrownBy(()->cod.remit(remit(accepted,"101",UUID.randomUUID()))).isInstanceOf(AppException.class);
        assertThat(cod.history(c.collectionId())).isEmpty();
    }
    @Test void oneReceiptAllocatesToDifferentCustomersWithoutMixingTheirPayments() throws Exception {
        var first=accept("22220000");
        UUID otherCustomer=UUID.randomUUID(), otherOrder=UUID.randomUUID(), otherDelivery=UUID.randomUUID();
        jdbc.update("INSERT INTO customers(customer_id,customer_code,customer_name,phone) VALUES (?,?,'Other COD customer',?)",
                otherCustomer,"C-"+otherCustomer,"09"+System.nanoTime());
        jdbc.update("""
            INSERT INTO orders(order_id,order_code,customer_id,order_channel,order_status,payment_status,order_date,total_amount)
            VALUES (?,?,?,'ONLINE','PROCESSING','UNPAID',now(),100)
            """,otherOrder,"O-"+otherOrder,otherCustomer);
        jdbc.update("""
            INSERT INTO deliveries(delivery_id,delivery_code,order_id,warehouse_code,shipper_id,delivery_status,
            recipient_name,recipient_phone,delivery_address,cod_required,cod_amount,created_by)
            VALUES (?,?,?,'MAIN',?,'DELIVERED','Other','0900000000','Test',true,100,?)
            """,otherDelivery,"D-"+otherDelivery,otherOrder,shipper,accountant);
        as(shipper,"SHIPPER");
        var second=cod.claim(new Claim(otherDelivery,new BigDecimal("100"),OffsetDateTime.now(),null,UUID.randomUUID()));
        as(accountant,"ACCOUNTANT");
        second=cod.review(second.collectionId(),new Review(0,null),true);
        var receipt=cod.remit(new Remit(UUID.randomUUID(),shipper,new BigDecimal("22220100"),List.of(
                new Allocation(first.collectionId(),first.amount(),first.version()),
                new Allocation(second.collectionId(),second.amount(),second.version())),null));
        assertThat(receipt.items()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM transactions WHERE order_id=?",UUID.class,otherOrder)).isEqualTo(otherCustomer);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM transactions WHERE order_id=?",UUID.class,order)).isEqualTo(customer);
        assertThat(status()).isEqualTo("PAID");
    }
    @Test void existingDepositPlusOneDeliveryDoesNotPayTheWholeProjectOrder() throws Exception {
        jdbc.update("UPDATE orders SET total_amount=30000000 WHERE order_id=?",order);
        as(accountant,"ACCOUNTANT");
        payments.record(order,new RecordOrderPaymentRequest(UUID.randomUUID(),new BigDecimal("10000000"),
                RecordOrderPaymentRequest.Method.CASH,null,"Cọc"));
        var c=accept("8000000");
        cod.remit(remit(c,"8000000",UUID.randomUUID()));
        assertThat(status()).isEqualTo("PARTIALLY_PAID");
        assertThat(cod.balance(order).storeOutstanding()).isEqualByComparingTo("12000000");
    }
    @Test void changedDeliveryCannotBeAcceptedButCanStillBeRejected() throws Exception {
        var c=submit("22220000");
        as(accountant,"ACCOUNTANT");
        for (var change:List.of("delivery_status='IN_TRANSIT'", "cod_required=false", "cod_amount=22221000")) {
            jdbc.update("UPDATE deliveries SET delivery_status='DELIVERED',cod_required=true,cod_amount=22220000 WHERE delivery_id=?",delivery);
            jdbc.update("UPDATE deliveries SET "+change+" WHERE delivery_id=?",delivery);
            assertThatThrownBy(()->cod.review(c.collectionId(),new Review(c.version(),null),true))
                    .isInstanceOfSatisfying(AppException.class,e->assertThat(e.getErrorCode()).isEqualTo(CodErrorCode.CONFLICT));
            assertThat(cod.detail(c.collectionId()).reviewStatus()).isEqualTo("SUBMITTED");
            assertThat(cod.detail(c.collectionId()).version()).isEqualTo(c.version());
            assertThat(cod.balance(order).shipperHeld()).isZero();
        }
        assertThat(cod.review(c.collectionId(),new Review(c.version(),"Delivery changed; reconcile with fulfillment"),false)
                .reviewStatus()).isEqualTo("REJECTED");
        assertThat(status()).isEqualTo("UNPAID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE order_id=?",Integer.class,order)).isZero();
    }
    @Test void deliveryMovedToAnotherOrderCannotFundTheOriginalOrder() throws Exception {
        var c=submit("100");
        UUID otherOrder=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders(order_id,order_code,customer_id,order_channel,order_status,payment_status,order_date,total_amount)
                VALUES (?,?,?,'ONLINE','PROCESSING','UNPAID',now(),22220000)
                """,otherOrder,"O-"+otherOrder,customer);
        jdbc.update("UPDATE deliveries SET order_id=? WHERE delivery_id=?",otherOrder,delivery);
        as(accountant,"ACCOUNTANT");
        assertThatThrownBy(()->cod.review(c.collectionId(),new Review(c.version(),null),true))
                .isInstanceOfSatisfying(AppException.class,e->assertThat(e.getErrorCode()).isEqualTo(CodErrorCode.CONFLICT));
        assertThat(cod.detail(c.collectionId()).reviewStatus()).isEqualTo("SUBMITTED");
    }
    @Test void reassignmentPreservesOriginalCollectorAndAcceptedMoneyCanStillBeRemitted() throws Exception {
        var c=submit("100");
        UUID replacement=user();
        jdbc.update("UPDATE deliveries SET shipper_id=? WHERE delivery_id=?",replacement,delivery);
        as(accountant,"ACCOUNTANT");
        var accepted=cod.review(c.collectionId(),new Review(c.version(),null),true);
        assertThat(accepted.shipperId()).isEqualTo(shipper);
        jdbc.update("UPDATE deliveries SET delivery_status='IN_TRANSIT',cod_required=false WHERE delivery_id=?",delivery);
        assertThat(cod.review(c.collectionId(),new Review(c.version(),null),true).collectionId()).isEqualTo(c.collectionId());
        assertThat(cod.remit(remit(accepted,"100",UUID.randomUUID())).shipperId()).isEqualTo(shipper);
        assertThat(cod.detail(c.collectionId()).remittanceStatus()).isEqualTo("SETTLED");
    }
    @Test void directPaymentWhileClaimIsPendingRequiresRejectionBeforeDebtActionsResume() throws Exception {
        var c=submit("22220000");
        as(accountant,"ACCOUNTANT");
        payments.record(order,new RecordOrderPaymentRequest(UUID.randomUUID(),new BigDecimal("22220000"),
                RecordOrderPaymentRequest.Method.CASH,null,"Direct receipt while COD is pending"));
        assertThatThrownBy(()->cod.review(c.collectionId(),new Review(c.version(),null),true))
                .isInstanceOfSatisfying(AppException.class,e->assertThat(e.getErrorCode()).isEqualTo(CodErrorCode.CONFLICT));
        assertThat(debts.detail(customer).summary().codReviewRequired()).isTrue();
        cod.review(c.collectionId(),new Review(c.version(),"Direct receipt verified; COD claim reconciled as duplicate"),false);
        assertThat(debts.detail(customer).summary().codReviewRequired()).isFalse();
        assertThat(cod.balance(order).pendingClaimAmount()).isZero();
        assertThat(cod.balance(order).shipperHeld()).isZero();
        assertThat(status()).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE order_id=?",Integer.class,order)).isEqualTo(1);
    }
}
