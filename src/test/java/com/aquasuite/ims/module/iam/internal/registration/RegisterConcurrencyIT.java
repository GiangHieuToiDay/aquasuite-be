package com.aquasuite.ims.module.iam.internal.registration;

import com.aquasuite.ims.module.iam.internal.payload.request.RegisterRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.ResendRegistrationOtpRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.VerifyRegistrationRequest;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.extension.ExtendWith(
        org.springframework.boot.test.system.OutputCaptureExtension.class)
class RegisterConcurrencyIT extends RegisterTestSupport {
    private List<String> race(Supplier<String> first, Supplier<String> second) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a =
                    pool.submit(
                            () -> {
                                start.await();
                                return first.get();
                            });
            var b =
                    pool.submit(
                            () -> {
                                start.await();
                                return second.get();
                            });
            start.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }
    }

    private String outcome(Runnable action) {
        try {
            action.run();
            return "SUCCESS";
        } catch (AppException ex) {
            return ex.getErrorCode().getCode();
        }
    }

    @Test
    void twoVerificationsCreateOnlyOneSession() throws Exception {
        registered();
        String code = otp();
        var req = new VerifyRegistrationRequest(EMAIL, code);
        var results =
                race(
                        () -> outcome(() -> registrations.verify(req)),
                        () -> outcome(() -> registrations.verify(req)));
        assertThat(results).containsExactlyInAnyOrder("SUCCESS", "REG_006");
        assertThat(redis.keys("auth:v2:session:*")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void twoRegistrationsWithSameCredentialsCreateOneAccount(
            org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        var req = new RegisterRequest("Nguyen An", EMAIL, "0901234567", PASSWORD, PASSWORD, true);
        var results =
                race(
                        () -> outcome(() -> registrations.register(req)),
                        () -> outcome(() -> registrations.register(req)));
        assertThat(results).containsExactlyInAnyOrder("SUCCESS", "REG_001");
        assertThat(users.count()).isEqualTo(1);
        assertThat(output.getAll())
                .doesNotContain("Batch entry 0 insert into public.users", "$2a$12$");
    }

    @Test
    void resendAndVerifyCannotActivateAnInvalidatedCode() throws Exception {
        var user = registered();
        String old = otp();
        redis.opsForHash()
                .put(key(user), "sentAt", Long.toString(System.currentTimeMillis() - 60_001));
        var results =
                race(
                        () ->
                                outcome(
                                        () ->
                                                registrations.resend(
                                                        new ResendRegistrationOtpRequest(EMAIL))),
                        () ->
                                outcome(
                                        () ->
                                                registrations.verify(
                                                        new VerifyRegistrationRequest(
                                                                EMAIL, old))));
        // Either verification locks first, or resend invalidates the old code before verification.
        assertThat(
                        results.equals(List.of("REG_006", "SUCCESS"))
                                || results.equals(List.of("SUCCESS", "REG_002")))
                .isTrue();
    }
}
