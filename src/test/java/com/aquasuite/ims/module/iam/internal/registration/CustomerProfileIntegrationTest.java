package com.aquasuite.ims.module.iam.internal.registration;

import com.aquasuite.ims.module.crm.api.dto.CustomerRegistrationRequest;
import com.aquasuite.ims.module.iam.internal.user.enums.UserStatus;
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

    @Test void crmFailureRollsBackProfileAndActivationThenResendRecovers() throws Exception {
        var user=registered(); String code=otp();
        com.aquasuite.ims.module.crm.api.CustomerRegistrationApi target =
                org.springframework.test.util.AopTestUtils.getUltimateTargetObject(customerProfiles);
        doAnswer(call -> {
            call.callRealMethod();
            throw new IllegalStateException("synthetic CRM failure after insert");
        }).when(target).createProfile(any());
        postJson(ROOT+"/verifications",Map.of("email",EMAIL,"otp",code),503);
        assertThat(users.findById(user.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers",Integer.class)).isZero();
        reset(target);
        postJson(ROOT+"/otp-resends",Map.of("email",EMAIL),200);
        postJson(ROOT+"/verifications",Map.of("email",EMAIL,"otp",otp()),200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE user_id=?",Integer.class,user.getId())).isEqualTo(1);
    }

    @Test void publicCrmContractRequiresTransactionAndIsIdempotentWithoutOverwriting() throws Exception {
        var user=registered(); user.setStatus(UserStatus.ACTIVE); users.saveAndFlush(user);
        var request=new CustomerRegistrationRequest(user.getId(),user.getFullName(),user.getPhone(),user.getEmail());
        assertThatThrownBy(() -> customerProfiles.createProfile(request))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        var first=transactions.execute(tx -> customerProfiles.createProfile(request));
        var second=transactions.execute(tx -> customerProfiles.createProfile(request));
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers",Integer.class)).isEqualTo(1);
        var changed=new CustomerRegistrationRequest(user.getId(),"Different Name",user.getPhone(),user.getEmail());
        assertThatThrownBy(() -> transactions.execute(tx -> customerProfiles.createProfile(changed)))
                .isInstanceOfSatisfying(AppException.class,ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo("DB_001"));
        assertThat(jdbc.queryForObject("SELECT customer_name FROM customers WHERE user_id=?",String.class,user.getId()))
                .isEqualTo(user.getFullName());
    }

    @Test void iamAndCrmCommunicateOnlyThroughPublicContracts() {
        var classes=new com.tngtech.archunit.core.importer.ClassFileImporter().importPackages(
                "com.aquasuite.ims.module.iam.internal.registration","com.aquasuite.ims.module.crm");
        for(var type:classes) for(var dep:type.getDirectDependenciesFromSelf()) {
            String target=dep.getTargetClass().getPackageName();
            if(!target.startsWith("com.aquasuite.ims.module.")) continue;
            String own=type.getPackageName().split("[.]")[4], other=target.split("[.]")[4];
            if(!own.equals(other)) assertThat(target).contains(".api");
        }
    }
}
