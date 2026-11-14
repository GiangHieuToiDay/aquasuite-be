package com.aquasuite.ims.module.finance.internal.controller;

import com.aquasuite.ims.module.finance.internal.service.impl.CodService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.internal.security.AccessAuthenticationConverter;
import com.aquasuite.ims.module.iam.internal.security.AuthSecurityConfig;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers={CodController.class,PaymentVerificationController.class}, excludeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,
        classes={AuthSecurityConfig.class,AccessAuthenticationConverter.class}))
@Import({GlobalExceptionHandler.class,CodControllerSecurityTest.Security.class})
class CodControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean CodService service;
    @MockBean IamApi iam;
    @MockBean com.aquasuite.ims.module.finance.internal.service.PaymentVerificationService verifications;

    @Test void anonymousCannotListCod() throws Exception {
        mvc.perform(get("/api/v1/finance/cod/collections")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void unrelatedRoleCannotReadCollection() throws Exception {
        mvc.perform(get("/api/v1/finance/cod/collections/{id}",UUID.randomUUID())
                .with(user("tech").roles("TECHNICAL_STAFF"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void shipperCannotApproveOwnOrOtherCollections() throws Exception {
        mvc.perform(post("/api/v1/finance/cod/collections/{id}/accept",UUID.randomUUID())
                .with(user("shipper").roles("SHIPPER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void salesCannotAcceptCollection() throws Exception {
        mvc.perform(post("/api/v1/finance/cod/collections/{id}/accept",UUID.randomUUID())
                .with(user("sales").roles("SALES_STAFF")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void shipperCanDeclareCollectionAsJsonWithoutPhoto() throws Exception {
        UUID delivery=UUID.randomUUID(), key=UUID.randomUUID();
        mvc.perform(post("/api/v1/finance/cod/collections").with(user("shipper").roles("SHIPPER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryId\":\""+delivery+"\",\"idempotencyKey\":\""+key
                        +"\",\"amount\":100,\"collectedAt\":\"2026-11-09T00:00:00+07:00\"}"))
                .andExpect(status().isOk());
        verify(service).claim(argThat(c->delivery.equals(c.deliveryId()) && key.equals(c.idempotencyKey())
                && c.amount().intValueExact()==100));
    }
    @Test void ownerCanAcceptCollection() throws Exception {
        UUID id=UUID.randomUUID();
        mvc.perform(post("/api/v1/finance/cod/collections/{id}/accept",id)
                .with(user("owner").roles("SHOP_OWNER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0}")).andExpect(status().isOk());
        verify(service).review(org.mockito.ArgumentMatchers.eq(id),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(true));
    }
    @Test void ownerCanVerifyBankTransfer() throws Exception {
        UUID id=UUID.randomUUID();
        mvc.perform(post("/api/v1/finance/payment-verifications/{id}/verify",id)
                .with(user("owner").roles("SHOP_OWNER"))).andExpect(status().isOk());
        verify(verifications).verify(id);
    }
    @Test void salesCannotVerifyBankTransfer() throws Exception {
        mvc.perform(post("/api/v1/finance/payment-verifications/{id}/verify",UUID.randomUUID())
                .with(user("sales").roles("SALES_STAFF"))).andExpect(status().isForbidden());
        verifyNoInteractions(verifications);
    }
    @TestConfiguration
    @EnableMethodSecurity
    static class Security {
        @Bean SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
            return http.csrf(AbstractHttpConfigurer::disable).authorizeHttpRequests(a->a.anyRequest().authenticated())
                    .httpBasic(b->{}).build();
        }
    }
}
