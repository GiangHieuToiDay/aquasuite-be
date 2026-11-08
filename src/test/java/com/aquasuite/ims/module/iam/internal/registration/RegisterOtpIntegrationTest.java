package com.aquasuite.ims.module.iam.internal.registration;

import com.aquasuite.ims.module.iam.internal.payload.response.LoginResponse;
import com.aquasuite.ims.module.iam.internal.payload.request.*;
import com.aquasuite.ims.module.iam.internal.payload.response.*;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import org.junit.jupiter.api.Test;
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
        assertThat(users.findById(user.getId())).isEmpty();
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
                .put(key(user), "sentAt", Long.toString(redisNow() - 59_000));
        postJson(ROOT + "/otp-resends", Map.of("email", EMAIL), 429);
        redis.opsForHash()
                .put(key(user), "sentAt", Long.toString(redisNow() - 60_001));
        postJson(ROOT + "/otp-resends", Map.of("email", EMAIL), 200);
        assertThat(redis.opsForHash().get(key(user), "attempts")).isEqualTo("1");
        String fresh = otp();
        assertThat(encoder.matches(fresh, redis.opsForHash().get(key(user), "otpHash").toString())).isTrue();
        wrong = fresh.equals("000000") ? "000001" : "000000";
        for (int i = 0; i < 3; i++)
            postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", wrong), 400);
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", wrong), 429);
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", fresh), 429);
        assertThat(redis.opsForHash().get(key(user), "attempts")).isEqualTo("5");
        assertThat(users.findById(user.getId())).isEmpty();
        assertThat(redis.getExpire(key(user))).isPositive();
    }

    @Test
    void resendSendsFreshEmailCode() throws Exception {
        var user = registered();
        redis.opsForHash().put(key(user), "sentAt", Long.toString(redisNow() - 60_001));
        postJson(ROOT + "/otp-resends", Map.of("email", EMAIL), 200);
        verify(mail, times(2)).sendRegistrationOtp(eq(EMAIL), anyString());
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", otp()), 200);
    }

    @Test
    void expiredPendingStateRequiresRegisteringAgain() throws Exception {
        var user = registered();
        String code = otp();
        redis.expire(key(user), Duration.ofMillis(1));
        for (String phoneLock : redis.keys("iam:registration:phone:v4:*")) {
            redis.expire(phoneLock, Duration.ofMillis(1));
        }
        org.awaitility.Awaitility.await()
                .until(() -> !Boolean.TRUE.equals(redis.hasKey(key(user))));
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", code), 410);
        assertThat(users.findById(user.getId())).isEmpty();
        postJson(ROOT + "/otp-resends", Map.of("email", EMAIL), 410);
        postJson(ROOT, form(), 201);
    }

    @Test
    void mailFailureKeepsOnlyRedisPendingStateAndAllowsLaterResend() throws Exception {
        doThrow(new com.aquasuite.ims.share.exception.AppException(
                com.aquasuite.ims.module.iam.internal.error.RegistrationErrorCode.MAIL_UNAVAILABLE)).when(mail).sendRegistrationOtp(anyString(), anyString());
        postJson(ROOT, form(), 503);
        assertThat(users.count()).isZero();
        var user = new com.aquasuite.ims.module.iam.internal.entity.User();
        user.setEmail(EMAIL);
        reset(mail);
        redis.opsForHash()
                .put(key(user), "sentAt", Long.toString(redisNow() - 60_001));
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
    void creationFailureRollsBackUserAndReleasesClaimForRetry() throws Exception {
        var user = registered();
        String code = otp();
        doThrow(
                        new org.springframework.dao.DataAccessResourceFailureException(
                                "synthetic database failure"))
                .when(users)
                .saveAndFlush(argThat(u -> u != null && u.getStatus() == UserStatus.ACTIVE));
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", code), 503);
        reset(users);
        assertThat(users.findById(user.getId())).isEmpty();
        assertThat(redis.hasKey(key(user))).isTrue();
        assertThat(redis.opsForHash().hasKey(key(user), "claim")).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Integer.class)).isZero();
        assertThat(redis.opsForHash().get(key(user), "approvedHash")).isNotEqualTo(code);
        // Retries must accept only the saved hash of the approved code.
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", "000000"), 400);
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", code), 200);
        verify(mail, times(1)).sendRegistrationOtp(eq(EMAIL), anyString());
    }

    @Test
    void emailSendQuotaSurvivesResendAndPendingStateDoesNotExtend() throws Exception {
        var user = registered();
        var expiryScript = new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                "return redis.call('PEXPIRETIME', KEYS[1])", Long.class);
        Long expiresAt = redis.execute(expiryScript, java.util.List.of(key(user)));
        assertThat(expiresAt).isPositive();
        for (int i = 0; i < 2; i++) {
            redis.opsForHash().put(key(user), "sentAt", Long.toString(redisNow() - 60_001));
            postJson(ROOT + "/otp-resends", Map.of("email", EMAIL), 200);
        }
        redis.opsForHash().put(key(user), "sentAt", Long.toString(redisNow() - 60_001));
        postJson(ROOT + "/otp-resends", Map.of("email", EMAIL), 429);
        assertThat(redis.execute(expiryScript, java.util.List.of(key(user)))).isEqualTo(expiresAt);
        assertThat(redis.getExpire(key(user), java.util.concurrent.TimeUnit.MILLISECONDS)).isPositive();
        verify(mail, times(3)).sendRegistrationOtp(eq(EMAIL), anyString());
    }

    @Test
    void anotherEmailCannotReusePendingPhoneVerification() throws Exception {
        registered();
        var another = form();
        another.put("email", "another@example.test");
        postJson(ROOT, another, 409);
        verify(mail, times(1)).sendRegistrationOtp(eq(EMAIL), anyString());
        assertThat(users.count()).isZero();
    }
    @Test
    void claimedOtpCannotBeVerifiedOrRotatedUntilReleased() throws Exception {
        var pending = registered();
        String code = otp();
        var store = new com.aquasuite.ims.module.iam.internal.service.RegistrationOtpStore(redis, encoder, mail);
        var claim = store.verifyAndClaim(EMAIL, code);
        redis.opsForHash().put(key(pending), "sentAt", Long.toString(redisNow() - 60_001));
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", code), 409);
        postJson(ROOT + "/otp-resends", Map.of("email", EMAIL), 409);
        assertThat(users.count()).isZero();
        store.release(claim);
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", code), 200);
    }

    @Test
    void registeringSameEmailWithDifferentCaseCannotOverwritePendingPassword() throws Exception {
        var pending = registered();
        String original = redis.opsForHash().get(key(pending), "passwordHash").toString();
        var duplicate = form();
        duplicate.put("email", EMAIL.toUpperCase(java.util.Locale.ROOT));
        duplicate.put("password", "DifferentSynthetic1!");
        duplicate.put("passwordConfirmation", "DifferentSynthetic1!");
        postJson(ROOT, duplicate, 409);
        assertThat(redis.opsForHash().get(key(pending), "passwordHash")).isEqualTo(original);
        assertThat(users.count()).isZero();
    }

}
