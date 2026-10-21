package com.aquasuite.ims.module.iam.internal.auth.service;

import com.aquasuite.ims.module.iam.internal.service.InMemoryPasswordRecoveryStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryPasswordRecoveryStoreTest {

    private final InMemoryPasswordRecoveryStore store = new InMemoryPasswordRecoveryStore();
    private final Instant now = Instant.parse("2026-10-20T00:00:00Z");
    private final UUID userId = UUID.randomUUID();

    @Test
    void validOtpIsSingleUse() {
        store.put("email:user@example.com", userId, "hashed-otp", now);

        assertThat(store.consumeIfValid("email:user@example.com", "123456", this::matches, now))
                .isNotNull();
        assertThat(store.consumeIfValid("email:user@example.com", "123456", this::matches, now))
                .isNull();
    }

    @Test
    void expiredOtpIsRejected() {
        store.put("email:user@example.com", userId, "hashed-otp", now);

        assertThat(store.consumeIfValid("email:user@example.com", "123456", this::matches,
                now.plus(11, ChronoUnit.MINUTES))).isNull();
    }

    @Test
    void fifthInvalidOtpAttemptInvalidatesChallenge() {
        store.put("email:user@example.com", userId, "hashed-otp", now);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(store.consumeIfValid("email:user@example.com", "invalid", this::matches, now)).isNull();
        }

        assertThat(store.consumeIfValid("email:user@example.com", "123456", this::matches, now)).isNull();
    }

    @Test
    void fifthSendInDayWindowIsAllowedAndSixthIsRejected() {
        for (int send = 0; send < 5; send++) {
            assertThat(store.canSend("email:user@example.com", now)).isTrue();
            store.put("email:user@example.com", userId, "hashed-otp", now);
        }

        assertThat(store.canSend("email:user@example.com", now)).isFalse();
    }

    private boolean matches(String rawOtp, String otpHash) {
        return "123456".equals(rawOtp) && "hashed-otp".equals(otpHash);
    }
}
