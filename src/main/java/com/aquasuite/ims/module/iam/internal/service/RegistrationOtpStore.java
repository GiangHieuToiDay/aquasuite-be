package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.internal.error.RegistrationErrorCode;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Atomic pending-registration and OTP lifecycle, isolated from password recovery and sessions. */
@Component
@RequiredArgsConstructor
public class RegistrationOtpStore {
    public static final String PREFIX = "iam:registration:pending:v2:";
    private static final long TTL_MS = 300_000;
    private static final int MAX_ATTEMPTS = 5;
    private final StringRedisTemplate redis;
    private final PasswordEncoder encoder;

    private static final DefaultRedisScript<Long> SAVE = script("""
        if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
        local now = redis.call('TIME')
        local ms = now[1] * 1000 + math.floor(now[2] / 1000)
        redis.call('HSET', KEYS[1], 'id', ARGV[1], 'fullName', ARGV[2],
          'email', ARGV[3], 'phone', ARGV[4], 'passwordHash', ARGV[5],
          'hash', ARGV[6], 'version', ARGV[7], 'sentAt', ms, 'attempts', 0)
        redis.call('PEXPIRE', KEYS[1], ARGV[8])
        return 1
        """);
    private static final DefaultRedisScript<Long> ROTATE = script("""
        if redis.call('PTTL', KEYS[1]) <= 0 then return -1 end
        if redis.call('HEXISTS', KEYS[1], 'claim') == 1 then return -2 end
        local now = redis.call('TIME')
        local ms = now[1] * 1000 + math.floor(now[2] / 1000)
        if tonumber(redis.call('HGET', KEYS[1], 'attempts')) >= 5
           or ms - tonumber(redis.call('HGET', KEYS[1], 'sentAt')) < 60000 then return -3 end
        redis.call('HSET', KEYS[1], 'hash', ARGV[1], 'version', ARGV[2], 'sentAt', ms)
        redis.call('PEXPIRE', KEYS[1], ARGV[3])
        return 1
        """);
    private static final DefaultRedisScript<Long> VERIFY = script("""
        if redis.call('PTTL', KEYS[1]) <= 0 then return -1 end
        if redis.call('HEXISTS', KEYS[1], 'claim') == 1 then return -2 end
        if redis.call('HGET', KEYS[1], 'version') ~= ARGV[1] then return -4 end
        if tonumber(redis.call('HGET', KEYS[1], 'attempts')) >= 5 then return -3 end
        if ARGV[2] == 'false' then
          local count = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
          if count >= 5 then return -3 end
          return count
        end
        redis.call('HSET', KEYS[1], 'claim', ARGV[3])
        return 0
        """);
    private static final DefaultRedisScript<Long> FINISH = script("""
        if redis.call('HGET', KEYS[1], 'version') ~= ARGV[1]
           or redis.call('HGET', KEYS[1], 'claim') ~= ARGV[2] then return 0 end
        if ARGV[3] == 'complete' then return redis.call('DEL', KEYS[1]) end
        return redis.call('HDEL', KEYS[1], 'claim')
        """);

    public void save(PendingRegistration registration, String otpHash) {
        Long result = redis.execute(SAVE, List.of(key(registration.email())),
                registration.id().toString(), registration.fullName(), registration.email(),
                registration.phone(), registration.passwordHash(), otpHash,
                UUID.randomUUID().toString(), Long.toString(TTL_MS));
        if (result == null) throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        if (result != 1) throw new AppException(RegistrationErrorCode.DUPLICATE_ACCOUNT);
    }

    public void rotateOtp(String email, String otpHash) {
        check(redis.execute(ROTATE, List.of(key(email)), otpHash,
                UUID.randomUUID().toString(), Long.toString(TTL_MS)));
    }

    public Claim verifyAndClaim(String email, String otp) {
        Map<Object, Object> state = redis.opsForHash().entries(key(email));
        if (state.isEmpty()) throw new AppException(RegistrationErrorCode.EXPIRED_OTP);
        String version = value(state, "version");
        String token = UUID.randomUUID().toString();
        boolean matches = encoder.matches(otp, value(state, "hash"));
        Long result = redis.execute(VERIFY, List.of(key(email)), version,
                Boolean.toString(matches), token);
        check(result);
        if (result > 0) {
            throw new AppException(RegistrationErrorCode.INVALID_OTP,
                    "Invalid OTP code. Remaining attempts: " + (MAX_ATTEMPTS - result));
        }
        PendingRegistration pending = new PendingRegistration(
                UUID.fromString(value(state, "id")), value(state, "fullName"),
                value(state, "email"), value(state, "phone"), value(state, "passwordHash"));
        return new Claim(pending, version, token);
    }

    public void complete(Claim claim) {
        finish(claim, "complete");
    }

    public void release(Claim claim) {
        finish(claim, "release");
    }

    private void finish(Claim claim, String action) {
        Long result = redis.execute(FINISH, List.of(key(claim.registration().email())),
                claim.version(), claim.token(), action);
        if (result == null) throw new AppException(RegistrationErrorCode.UNAVAILABLE);
    }

    private static void check(Long result) {
        if (result == null) throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        if (result == -1) throw new AppException(RegistrationErrorCode.EXPIRED_OTP);
        if (result == -2) throw new AppException(RegistrationErrorCode.NOT_PENDING);
        if (result == -3) throw new AppException(RegistrationErrorCode.OTP_LIMIT);
        if (result == -4) throw new AppException(RegistrationErrorCode.INVALID_OTP);
    }

    private static String value(Map<Object, Object> state, String field) {
        if (state.get(field) instanceof String text) return text;
        throw new AppException(RegistrationErrorCode.UNAVAILABLE);
    }

    public static String key(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private static DefaultRedisScript<Long> script(String text) {
        return new DefaultRedisScript<>(text, Long.class);
    }

    public record Claim(PendingRegistration registration, String version, String token) {
        @Override
        public String toString() {
            return "RegistrationClaim[redacted]";
        }
    }
}
