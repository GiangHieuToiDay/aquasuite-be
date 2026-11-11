package com.aquasuite.ims.module.iam.internal.registration;

import com.aquasuite.ims.module.crm.api.dto.CustomerRegistrationRequest;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerProfileIntegrationTest extends RegisterTestSupport {
    @Autowired TransactionTemplate transactions;

    @Test
    void crmFailureRollsBackAccountAndProfileThenSameOtpRecovers() throws Exception {
        var user = registered();
        String code = otp();
        com.aquasuite.ims.module.crm.api.CustomerRegistrationApi target =
                org.springframework.test.util.AopTestUtils.getUltimateTargetObject(
                        customerProfiles);
        doAnswer(
                        call -> {
                            call.callRealMethod();
                            throw new IllegalStateException("synthetic CRM failure after insert");
                        })
                .when(target)
                .createProfile(any());
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", code), 503);
        assertThat(users.findById(user.getId())).isEmpty();
        assertThat(redis.opsForHash().hasKey(key(user), "claim")).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Integer.class)).isZero();
        reset(target);
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", code), 200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM customers WHERE user_id=?",
                                Integer.class,
                                user.getId()))
                .isEqualTo(1);
    }

    @Test
    void publicCrmContractRequiresTransactionAndIsIdempotentWithoutOverwriting() throws Exception {
        var user = registered();
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", otp()), 200);
        var request =
                new CustomerRegistrationRequest(
                        user.getId(), user.getFullName(), user.getPhone(), user.getEmail());
        assertThatThrownBy(() -> customerProfiles.createProfile(request))
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
        var first = transactions.execute(tx -> customerProfiles.createProfile(request));
        var second = transactions.execute(tx -> customerProfiles.createProfile(request));
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Integer.class))
                .isEqualTo(1);
        var changed =
                new CustomerRegistrationRequest(
                        user.getId(), "Different Name", "0907654321", user.getEmail());
        assertThatThrownBy(
                        () -> transactions.execute(tx -> customerProfiles.createProfile(changed)))
                .isInstanceOfSatisfying(
                        AppException.class,
                        ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo("DB_001"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT customer_name FROM customers WHERE user_id=?",
                                String.class,
                                user.getId()))
                .isEqualTo(user.getFullName());
    }

    @Test
    void emailOtpActivationLinksCounterCustomerAndKeepsPreviousOrder() throws Exception {
        java.util.UUID customer=java.util.UUID.randomUUID(), order=java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO customers(customer_id,customer_code,customer_name,phone) VALUES(?,?,?,?)",
                customer,"COUNTER-"+customer,"Existing counter name","0901234567");
        jdbc.update("INSERT INTO orders(order_id,order_code,customer_id,order_channel,order_status,payment_status,order_date,total_amount) VALUES(?,?,?,'IN_STORE','PROCESSING','UNPAID',current_timestamp,100)",
                order,"COUNTER-"+order,customer);
        try {
            var user=registered();
            assertThat(jdbc.queryForObject("SELECT user_id FROM customers WHERE customer_id=?",java.util.UUID.class,customer)).isNull();
            postJson(ROOT+"/verifications",Map.of("email",EMAIL,"otp",otp()),200);
            assertThat(jdbc.queryForObject("SELECT user_id FROM customers WHERE customer_id=?",java.util.UUID.class,customer)).isEqualTo(user.getId());
            assertThat(jdbc.queryForObject("SELECT customer_id FROM orders WHERE order_id=?",java.util.UUID.class,order)).isEqualTo(customer);
            assertThat(jdbc.queryForObject("SELECT customer_name FROM customers WHERE customer_id=?",String.class,customer)).isEqualTo("Existing counter name");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE phone='0901234567'",Integer.class)).isEqualTo(1);
            assertThat(users.findById(user.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
        } finally {
            jdbc.update("DELETE FROM orders WHERE order_id=?",order);
        }
    }

    @Test
    void iamAndCrmCommunicateOnlyThroughPublicContracts() {
        var classes =
                new com.tngtech.archunit.core.importer.ClassFileImporter()
                        .importPackages(
                                "com.aquasuite.ims.module.iam.internal.registration",
                                "com.aquasuite.ims.module.crm");
        for (var type : classes)
            for (var dep : type.getDirectDependenciesFromSelf()) {
                String target = dep.getTargetClass().getPackageName();
                if (!target.startsWith("com.aquasuite.ims.module.")) continue;
                String own = type.getPackageName().split("[.]")[4], other = target.split("[.]")[4];
                if (!own.equals(other)) assertThat(target).contains(".api");
            }
    }
}
