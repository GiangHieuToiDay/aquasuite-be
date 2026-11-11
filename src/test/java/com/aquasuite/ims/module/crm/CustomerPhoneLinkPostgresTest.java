package com.aquasuite.ims.module.crm;

import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.CustomerRegistrationApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerRegistrationRequest;
import com.aquasuite.ims.module.crm.internal.service.impl.CrmApiImpl;
import com.aquasuite.ims.module.crm.internal.service.impl.CustomerRegistrationServiceImpl;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false, properties = "spring.datasource.hikari.maximum-pool-size=4")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CrmApiImpl.class, CustomerRegistrationServiceImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CustomerPhoneLinkPostgresTest extends PostgreSqlIntegrationTestSupport {
    @Autowired CrmApi crm;
    @Autowired CustomerRegistrationApi profiles;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    private final String phone = "0901234567";
    private TransactionTemplate tx() { return new TransactionTemplate(manager); }
    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,username,email,password_hash,full_name,status) VALUES (?,?,?,?,?,'ACTIVE')",
                id, "link-"+id, id+"@example.test", "synthetic-hash", "Registered name");
        return id;
    }
    private CustomerRegistrationRequest registration(UUID id) {
        return new CustomerRegistrationRequest(id, "Registered name", phone, id+"@example.test");
    }

    @Test void repeatedCounterPurchasesReuseOneUnlinkedCustomer() {
        var first = crm.getOrCreateCounterCustomer("  "+phone+" ", "Counter name");
        var second = crm.getOrCreateCounterCustomer(phone, "Different name");
        assertThat(first.customerId()).isEqualTo(second.customerId());
        assertThat(second.userId()).isNull();
        assertThat(second.customerName()).isEqualTo("Counter name");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE phone=?", Integer.class,phone)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE phone=?", Integer.class,phone)).isZero();
    }

    @Test void activationLinksOriginalCustomerAndPreservesOrderAndCreditData() {
        var customer = crm.getOrCreateCounterCustomer(phone, "Counter name");
        UUID order = UUID.randomUUID();
        jdbc.update("INSERT INTO orders(order_id,order_code,customer_id,order_channel,order_status,payment_status,order_date,total_amount) VALUES(?,?,?,'IN_STORE','PROCESSING','UNPAID',current_timestamp,100)",
                order, "O-"+order, customer.customerId());
        jdbc.update("UPDATE customers SET email='old@example.test', credit_limit=500,credit_term_days=30 WHERE customer_id=?", customer.customerId());
        UUID id = user();
        var linked = tx().execute(status -> profiles.createProfile(registration(id)));
        assertThat(linked.customerId()).isEqualTo(customer.customerId());
        assertThat(crm.getCustomerByUserId(id).orElseThrow().customerName()).isEqualTo("Counter name");
        assertThat(crm.getCustomerByUserId(id).orElseThrow().email()).isEqualTo("old@example.test");
        assertThat(jdbc.queryForObject("SELECT customer_id FROM orders WHERE order_id=?", UUID.class, order)).isEqualTo(customer.customerId());
        assertThat(jdbc.queryForObject("SELECT credit_limit FROM customers WHERE customer_id=?", java.math.BigDecimal.class,customer.customerId())).isEqualByComparingTo("500");
        var retried = tx().execute(status -> profiles.createProfile(registration(id)));
        assertThat(retried).isEqualTo(linked);
        jdbc.update("DELETE FROM orders WHERE order_id=?", order);
    }

    @Test void newRegistrationCreatesOneLinkedProfileAndPosReusesIt() {
        UUID id = user();
        var profile = tx().execute(status -> profiles.createProfile(registration(id)));
        var counter = crm.getOrCreateCounterCustomer(phone, "Counter name");
        assertThat(counter.customerId()).isEqualTo(profile.customerId());
        assertThat(counter.userId()).isEqualTo(id);
        assertThat(counter.customerName()).isEqualTo("Registered name");
    }

    @ParameterizedTest @ValueSource(strings={"INACTIVE","BLOCKED"})
    void activationCannotReactivateUnavailableProfile(String state) {
        var customer = crm.getOrCreateCounterCustomer(phone, "Counter name");
        jdbc.update("UPDATE customers SET status=? WHERE customer_id=?",state,customer.customerId());
        UUID id=user();
        assertThatThrownBy(() -> tx().execute(status -> profiles.createProfile(registration(id))))
                .isInstanceOf(AppException.class);
        assertThat(crm.getCustomerById(customer.customerId()).orElseThrow().userId()).isNull();
    }

    @Test void anotherUserCannotTakeAnAlreadyLinkedProfile() {
        UUID first=user(), second=user();
        var profile=tx().execute(status -> profiles.createProfile(registration(first)));
        assertThatThrownBy(() -> tx().execute(status -> profiles.createProfile(registration(second))))
                .isInstanceOfSatisfying(AppException.class,e -> assertThat(e.getErrorCode().getCode()).isEqualTo("DB_001"));
        assertThat(crm.getCustomerById(profile.customerId()).orElseThrow().userId()).isEqualTo(first);
    }

    @Test void failedActivationRollsBackUserAndCustomerLink() {
        var customer=crm.getOrCreateCounterCustomer(phone,"Counter name");
        UUID id=UUID.randomUUID();
        assertThatThrownBy(() -> tx().execute(status -> {
            jdbc.update("INSERT INTO users(id,username,email,password_hash,status) VALUES(?,?,?,?,'ACTIVE')",id,"link-"+id,id+"@example.test","synthetic-hash");
            profiles.createProfile(registration(id));
            throw new IllegalStateException("synthetic activation failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id=?",Integer.class,id)).isZero();
        assertThat(crm.getCustomerById(customer.customerId()).orElseThrow().userId()).isNull();
    }

    @Test void failedCounterOrderRollsBackItsNewProfile() {
        assertThatThrownBy(() -> tx().execute(status -> {
            crm.getOrCreateCounterCustomer(phone,"Counter name");
            throw new IllegalStateException("synthetic order failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(crm.getCustomerByPhone(phone)).isEmpty();
    }

    @Test void concurrentPosAndRegistrationCreateOnlyOneProfile() throws Exception {
        UUID id=user();
        var start=new CountDownLatch(1);
        try (var workers=Executors.newFixedThreadPool(2)) {
            var pos=workers.submit(() -> { start.await(); return crm.getOrCreateCounterCustomer(phone,"Counter name"); });
            var registration=workers.submit(() -> { start.await(); return tx().execute(status -> profiles.createProfile(registration(id))); });
            start.countDown();
            assertThat(pos.get(15,TimeUnit.SECONDS).customerId()).isEqualTo(registration.get(15,TimeUnit.SECONDS).customerId());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE phone=?",Integer.class,phone)).isEqualTo(1);
        assertThat(crm.getCustomerByUserId(id)).isPresent();
    }

    @Test void concurrentCounterPurchasesReuseOneProfile() throws Exception {
        var start=new CountDownLatch(1);
        try (var workers=Executors.newFixedThreadPool(2)) {
            var a=workers.submit(() -> { start.await(); return crm.getOrCreateCounterCustomer(phone,"First"); });
            var b=workers.submit(() -> { start.await(); return crm.getOrCreateCounterCustomer(phone,"Second"); });
            start.countDown();
            assertThat(a.get(15,TimeUnit.SECONDS).customerId()).isEqualTo(b.get(15,TimeUnit.SECONDS).customerId());
        }
    }

    @Test void concurrentAnonymousPurchasesKeepOneWalkInProfile() throws Exception {
        var start=new CountDownLatch(1);
        try (var workers=Executors.newFixedThreadPool(2)) {
            var a=workers.submit(() -> { start.await(); return crm.getOrCreateWalkInCustomer(); });
            var b=workers.submit(() -> { start.await(); return crm.getOrCreateWalkInCustomer(); });
            start.countDown();
            var first=a.get(15,TimeUnit.SECONDS);
            var second=b.get(15,TimeUnit.SECONDS);
            assertThat(first.customerId()).isEqualTo(second.customerId());
            assertThat(first.customerCode()).isEqualTo("CUS-WALKIN");
            assertThat(first.userId()).isNull();
        }
    }

    @Test void invalidPhonesCannotBecomeCustomersOrAnonymousFallback() {
        for (String invalid : new String[]{"", "0000000000", "+84901234567", "090123456"}) {
            assertThatThrownBy(() -> crm.getOrCreateCounterCustomer(invalid,"Counter name")).isInstanceOf(AppException.class);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers",Integer.class)).isZero();
    }
}
