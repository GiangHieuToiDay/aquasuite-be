package com.aquasuite.ims.module.iam.internal.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryPasswordRecoveryStore {

    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_SENDS = 5;
    private final ConcurrentHashMap<String, Challenge> challenges = new ConcurrentHashMap<>();

    public boolean canSend(String key, Instant now) {
        Challenge current = challenges.get(key);
        return current == null || now.isAfter(current.sendWindowStartedAt().plus(1, ChronoUnit.DAYS))
                || current.sendCount() < MAX_SENDS;
    }

    public void put(String key, UUID userId, String otpHash, Instant now) {
        challenges.compute(key, (ignored, current) -> {
            int sends = current != null && !now.isAfter(current.sendWindowStartedAt().plus(1, ChronoUnit.DAYS))
                    ? current.sendCount() + 1 : 1;
            return new Challenge(userId, otpHash, now.plus(10, ChronoUnit.MINUTES), 0, sends, now);
        });
    }

    public synchronized Challenge consumeIfValid(String key, String rawOtp, OtpMatcher matcher, Instant now) {
        Challenge challenge = challenges.get(key);
        if (challenge == null || now.isAfter(challenge.expiresAt()) || challenge.attempts() >= MAX_ATTEMPTS) {
            challenges.remove(key);
            return null;
        }
        if (!matcher.matches(rawOtp, challenge.otpHash())) {
            int nextAttempts = challenge.attempts() + 1;
            if (nextAttempts >= MAX_ATTEMPTS) {
                challenges.remove(key, challenge);
            } else {
                challenges.replace(key, challenge, challenge.withAttempts(nextAttempts));
            }
            return null;
        }
        return challenges.remove(key, challenge) ? challenge : null;
    }

    @FunctionalInterface
    public interface OtpMatcher { boolean matches(String rawOtp, String otpHash); }

    public record Challenge(UUID userId, String otpHash, Instant expiresAt, int attempts,
                            int sendCount, Instant sendWindowStartedAt) {
        Challenge withAttempts(int value) { return new Challenge(userId, otpHash, expiresAt, value, sendCount, sendWindowStartedAt); }
    }
}
