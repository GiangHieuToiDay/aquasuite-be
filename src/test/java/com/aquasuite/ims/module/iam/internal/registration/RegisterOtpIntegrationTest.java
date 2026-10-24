package com.aquasuite.ims.module.iam.internal.registration;

import com.aquasuite.ims.module.iam.internal.payload.response.LoginResponse;
import com.aquasuite.ims.module.iam.internal.payload.request.*;
import com.aquasuite.ims.module.iam.internal.payload.response.*;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import java.time.Duration;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegisterOtpIntegrationTest extends RegisterTestSupport {
    @Test
    void redisFailureCannotActivateAccount() throws Exception {
        var user = registered();
        String code = otp();
        doThrow(
                        new org.springframework.data.redis.RedisConnectionFailureException(
                                "synthetic Redis outage"))
                .when(redis)
                .opsForHash();
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", code), 503);
        reset(redis);
        assertThat(users.findById(user.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.PENDING_ACTIVATION);
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", code), 200);
    }

    @Test
    void wrongAttemptsPersistAndResendDoesNotResetThem() throws Exception {
        var user = registered();
        String code = otp();
        String wrong = code.equals("000000") ? "000001" : "000000";
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", wrong), 400);
        assertThat(redis.opsForHash().get(key(user), "attempts")).isEqualTo("1");
        redis.opsForHash()
                .put(key(user), "sentAt", Long.toString(System.currentTimeMillis() - 59_000));
        postJson(ROOT + "/otp-resends", Map.of("email", EMAIL), 429);
        redis.opsForHash()
                .put(key(user), "sentAt", Long.toString(System.currentTimeMillis() - 60_001));
        postJson(ROOT + "/otp-resends", Map.of("email", EMAIL), 200);
        assertThat(redis.opsForHash().get(key(user), "attempts")).isEqualTo("1");
        String fresh = otp();
        wrong = fresh.equals("000000") ? "000001" : "000000";
        for (int i = 0; i < 3; i++)
            postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", wrong), 400);
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", wrong), 429);
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", fresh), 429);
        assertThat(redis.opsForHash().get(key(user), "attempts")).isEqualTo("5");
        assertThat(users.findById(user.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(redis.getExpire(key(user))).isPositive();
    }

    @Test
    void resendInvalidatesOldOtpAndSupportsLeadingZero() throws Exception {
        var user = registered();
        String old = otp();
        redis.opsForHash()
                .put(key(user), "sentAt", Long.toString(System.currentTimeMillis() - 60_001));
        postJson(ROOT + "/otp-resends", Map.of("email", EMAIL), 200);
        // Force a deterministic leading-zero OTP using the same existing BCrypt verifier.
        redis.opsForHash().put(key(user), "hash", encoder.encode("012345"));
        if (!old.equals("012345"))
            postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", old), 400);
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", "012345"), 200);
    }

    @Test
    void expiredRedisStateCannotActivateAndCanBeResent() throws Exception {
        var user = registered();
        String code = otp();
        redis.expire(key(user), Duration.ofMillis(1));
        org.awaitility.Awaitility.await()
                .until(() -> !Boolean.TRUE.equals(redis.hasKey(key(user))));
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", code), 410);
        assertThat(users.findById(user.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.PENDING_ACTIVATION);
        postJson(ROOT + "/otp-resends", Map.of("email", EMAIL), 200);
    }

    @Test
    void mailFailureKeepsPendingAccountAndAllowsLaterResend() throws Exception {
        doThrow(new MailSendException("synthetic failure"))
                .when(mail)
                .send(any(SimpleMailMessage.class));
        postJson(ROOT, form(), 503);
        var user = users.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        reset(mail);
        redis.opsForHash()
                .put(key(user), "sentAt", Long.toString(System.currentTimeMillis() - 60_001));
        postJson(ROOT + "/otp-resends", Map.of("email", EMAIL), 200);
    }

    @Test
    void sessionFailureAfterActivationCommitsRequiresOrdinaryLogin() throws Exception {
        var user = registered();
        doThrow(new IllegalStateException("synthetic session failure"))
                .when(auth)
                .loginRegisteredUser(any());
        var result = postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", otp()), 503);
        assertThat(result.getResponse().getContentAsString())
                .contains("REG_009")
                .doesNotContain("access_token");
        assertThat(users.findById(user.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        reset(auth);
        postJson("/api/v1/auth/login", Map.of("email", EMAIL, "password", PASSWORD), 200);
    }

    @Test
    void activationFailureRollsBackUserAndRequiresResendAfterConsumption() throws Exception {
        var user = registered();
        String code = otp();
        doThrow(
                        new org.springframework.dao.DataAccessResourceFailureException(
                                "synthetic database failure"))
                .when(users)
                .saveAndFlush(argThat(u -> u != null && u.getStatus() == UserStatus.ACTIVE));
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", code), 503);
        reset(users);
        assertThat(users.findById(user.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(redis.hasKey(key(user))).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Integer.class)).isZero();
        postJson(ROOT + "/otp-resends", Map.of("email", EMAIL), 200);
    }
}
