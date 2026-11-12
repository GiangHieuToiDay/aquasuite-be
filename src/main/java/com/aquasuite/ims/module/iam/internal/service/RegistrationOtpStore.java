package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.internal.error.RegistrationErrorCode;
import com.aquasuite.ims.module.iam.internal.service.otp.RegistrationEmailSender;
import java.security.SecureRandom;
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

/**
 * Quản lý form đăng ký chờ, claim xử lý và giới hạn OTP trong Redis bằng Lua atomic.
 * Email OTP sinh/kiểm tra mã; phiên có TTL cố định 5 phút, tối đa 5 lần kiểm tra
 * và 3 lượt gửi trên mỗi email trong 10 phút, với thời gian chờ gửi lại 60 giây.
 */
@Component
@RequiredArgsConstructor
public class RegistrationOtpStore {
    // Isolate email challenges from pending SMS registrations.
    public static final String PREFIX = "iam:registration:pending:v4:";
    private static final long TTL_MS = 300_000;
    private static final int MAX_ATTEMPTS = 5;
    private final StringRedisTemplate redis;
    private final PasswordEncoder encoder;
    private final RegistrationEmailSender mail;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final DefaultRedisScript<Long> SAVE = script("""
        if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then return -5 end
        if tonumber(redis.call('GET', KEYS[3]) or '0') >= 3 then return -3 end
        local sends = redis.call('INCR', KEYS[3])
        if sends == 1 then redis.call('PEXPIRE', KEYS[3], 600000) end
        local now = redis.call('TIME')
        local ms = now[1] * 1000 + math.floor(now[2] / 1000)
        redis.call('HSET', KEYS[1], 'id', ARGV[1], 'fullName', ARGV[2],
          'email', ARGV[3], 'phone', ARGV[4], 'passwordHash', ARGV[5],
          'version', ARGV[6], 'claim', ARGV[7], 'sentAt', ms, 'attempts', 0)
        redis.call('PEXPIRE', KEYS[1], ARGV[8])
        redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[8])
        return 1
        """);
    private static final DefaultRedisScript<Long> RESEND = script("""
        if redis.call('PTTL', KEYS[1]) <= 0 then return -1 end
        if redis.call('HGET', KEYS[1], 'version') ~= ARGV[1] then return -4 end
        if redis.call('HEXISTS', KEYS[1], 'claim') == 1
           or redis.call('HEXISTS', KEYS[1], 'approvedHash') == 1 then return -2 end
        local now = redis.call('TIME')
        local ms = now[1] * 1000 + math.floor(now[2] / 1000)
        if tonumber(redis.call('HGET', KEYS[1], 'attempts')) >= 5
           or ms - tonumber(redis.call('HGET', KEYS[1], 'sentAt')) < 60000
           or tonumber(redis.call('GET', KEYS[3]) or '0') >= 3 then return -3 end
        local sends = redis.call('INCR', KEYS[3])
        if sends == 1 then redis.call('PEXPIRE', KEYS[3], 600000) end
        redis.call('HDEL', KEYS[1], 'otpHash')
        redis.call('HSET', KEYS[1], 'claim', ARGV[2], 'sentAt', ms, 'version', ARGV[3])
        return 1
        """);
    private static final DefaultRedisScript<Long> ACQUIRE = script("""
        if redis.call('PTTL', KEYS[1]) <= 0 then return -1 end
        if redis.call('HEXISTS', KEYS[1], 'claim') == 1 then return -2 end
        if redis.call('HGET', KEYS[1], 'version') ~= ARGV[1] then return -4 end
        if tonumber(redis.call('HGET', KEYS[1], 'attempts')) >= 5 then return -3 end
        redis.call('HINCRBY', KEYS[1], 'attempts', 1)
        redis.call('HSET', KEYS[1], 'claim', ARGV[2])
        return 1
        """);
    private static final DefaultRedisScript<Long> SENT = script("""
        if redis.call('PTTL', KEYS[1]) <= 0 then return -1 end
        if redis.call('HGET', KEYS[1], 'version') ~= ARGV[1]
           or redis.call('HGET', KEYS[1], 'claim') ~= ARGV[2] then return -2 end
        redis.call('HSET', KEYS[1], 'otpHash', ARGV[3])
        redis.call('HDEL', KEYS[1], 'claim')
        return 1
        """);
    private static final DefaultRedisScript<Long> APPROVE = script("""
        if redis.call('PTTL', KEYS[1]) <= 0 then return -1 end
        if redis.call('HGET', KEYS[1], 'version') ~= ARGV[1]
           or redis.call('HGET', KEYS[1], 'claim') ~= ARGV[2] then return -2 end
        redis.call('HSET', KEYS[1], 'approvedHash', ARGV[3])
        return 1
        """);
    private static final DefaultRedisScript<Long> FINISH = script("""
        if redis.call('HGET', KEYS[1], 'version') ~= ARGV[1]
           or redis.call('HGET', KEYS[1], 'claim') ~= ARGV[2] then return 0 end
        if ARGV[3] == 'complete' then
          if redis.call('GET', KEYS[2]) == ARGV[4] then redis.call('DEL', KEYS[2]) end
          return redis.call('DEL', KEYS[1])
        end
        return redis.call('HDEL', KEYS[1], 'claim')
        """);

    /** Giữ email/SĐT cho một phiên duy nhất, tính quota gửi và gửi OTP sau khi lưu form thành công. */
    public void save(PendingRegistration registration) {
        Claim claim = new Claim(registration, UUID.randomUUID().toString(), UUID.randomUUID().toString());
        check(redis.execute(SAVE, keys(registration), registration.id().toString(),
                registration.fullName(), registration.email(), registration.phone(),
                registration.passwordHash(), claim.version(), claim.token(), Long.toString(TTL_MS)));
        send(claim);
    }

    /** Giữ claim gửi lại và đổi version để chặn thao tác dùng snapshot cũ; không gia hạn TTL phiên. */
    public void rotateOtp(String email) {
        Claim previous = claim(state(email));
        Claim claim = new Claim(previous.registration(), UUID.randomUUID().toString(), previous.token());
        check(redis.execute(RESEND, keys(claim.registration()), previous.version(), claim.token(), claim.version()));
        send(claim);
    }

    /** Gửi email và lưu hash OTP đúng claim; khi lỗi, nhả claim nhưng giữ quota và form chờ. */
    private void send(Claim claim) {
        try {
            String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
            String hash = encoder.encode(code);
            mail.sendRegistrationOtp(claim.registration().email(), code);
            check(redis.execute(SENT, List.of(key(claim.registration().email())),
                    claim.version(), claim.token(), hash));
        } catch (RuntimeException ex) {
            releaseSafely(claim);
            throw ex;
        }
    }

    /**
     * Tăng số lần kiểm tra và giữ claim trước khi kiểm tra hash OTP, ngăn xác thực/gửi lại đồng thời.
     * Lưu hash mã đã được duyệt để có thể thử lại sau rollback mà không tiêu thụ approval lần nữa.
     *
     * @return claim còn được giữ để caller tạo tài khoản rồi complete hoặc release
     */
    public Claim verifyAndClaim(String email, String otp) {
        Map<Object, Object> state = state(email);
        Claim claim = claim(state);
        check(redis.execute(ACQUIRE, List.of(key(email)), claim.version(), claim.token()));
        try {
            // A saved hash permits retries after DB rollback
            // using only the approved code, within the original pending registration TTL.
            String approvedHash = (String) state.get("approvedHash");
            boolean matches = approvedHash != null ? encoder.matches(otp, approvedHash)
                    : state.get("otpHash") instanceof String hash && encoder.matches(otp, hash);
            if (!matches) {
                int attempts = Integer.parseInt(value(state, "attempts")) + 1;
                throw new AppException(attempts >= MAX_ATTEMPTS
                        ? RegistrationErrorCode.OTP_LIMIT : RegistrationErrorCode.INVALID_OTP);
            }
            check(redis.execute(APPROVE, List.of(key(email)), claim.version(), claim.token(),
                    approvedHash == null ? encoder.encode(otp) : approvedHash));
            return claim;
        } catch (RuntimeException ex) {
            releaseSafely(claim);
            throw ex;
        }
    }

    /** Xóa phiên và khóa SĐT thuộc claim sau khi tài khoản đã commit; giữ nguyên bộ đếm quota gửi. */
    public void complete(Claim claim) { finish(claim, "complete"); }

    /** Nhả claim đúng version/token để cho phép thử lại trong thời gian còn lại của phiên. */
    public void release(Claim claim) { finish(claim, "release"); }

    /** Nhả claim khi gửi/kiểm tra lỗi; lỗi Redis không được che mất lỗi gốc của thao tác. */
    private void releaseSafely(Claim claim) {
        try { release(claim); } catch (RuntimeException ignored) {
            // An uncertain claim fails closed until the pending TTL expires.
        }
    }

    /** Thực hiện complete/release bằng Lua, chỉ tác động khi version và token vẫn khớp. */
    private void finish(Claim claim, String action) {
        Long result = redis.execute(FINISH, keys(claim.registration()),
                claim.version(), claim.token(), action, claim.registration().id().toString());
        if (result == null) throw new AppException(RegistrationErrorCode.UNAVAILABLE);
    }

    /** Đọc snapshot phiên theo email; phiên không còn tồn tại được coi là đã hết hạn. */
    private Map<Object, Object> state(String email) {
        Map<Object, Object> state = redis.opsForHash().entries(key(email));
        if (state.isEmpty()) throw new AppException(RegistrationErrorCode.EXPIRED_OTP);
        return state;
    }

    /** Khôi phục dữ liệu đăng ký từ snapshot và sinh token mới để xin quyền xử lý phiên. */
    private static Claim claim(Map<Object, Object> state) {
        var pending = new PendingRegistration(UUID.fromString(value(state, "id")),
                value(state, "fullName"), value(state, "email"), value(state, "phone"),
                value(state, "passwordHash"));
        return new Claim(pending, value(state, "version"), UUID.randomUUID().toString());
    }

    /** Tạo các key phiên, khóa SĐT và quota gửi; email/SĐT được hash trong tên key. */
    private static List<String> keys(PendingRegistration registration) {
        String phoneDigest = digest(registration.phone());
        return List.of(key(registration.email()), "iam:registration:phone:v4:" + phoneDigest,
                "iam:registration:sends:v4:" + digest(registration.email().trim().toLowerCase(Locale.ROOT)));
    }

    /** Chuyển mã kết quả Lua thành lỗi nghiệp vụ cho hết hạn, tranh chấp, giới hạn hoặc trùng đăng ký. */
    private static void check(Long result) {
        if (result == null) throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        if (result == -1) throw new AppException(RegistrationErrorCode.EXPIRED_OTP);
        if (result == -2) throw new AppException(RegistrationErrorCode.NOT_PENDING);
        if (result == -3) throw new AppException(RegistrationErrorCode.OTP_LIMIT);
        if (result == -4) throw new AppException(RegistrationErrorCode.INVALID_OTP);
        if (result == -5) throw new AppException(RegistrationErrorCode.DUPLICATE_ACCOUNT);
    }

    /** Lấy trường chuỗi bắt buộc; từ chối snapshot thiếu dữ liệu thay vì tiếp tục tạo tài khoản. */
    private static String value(Map<Object, Object> state, String field) {
        if (state.get(field) instanceof String text) return text;
        throw new AppException(RegistrationErrorCode.UNAVAILABLE);
    }

    /** Chuẩn hóa email không phân biệt hoa/thường và tạo key trong namespace email v4. */
    public static String key(String email) {
        return PREFIX + digest(email.trim().toLowerCase(Locale.ROOT));
    }

    /** Hash SHA-256 để không đưa trực tiếp email/SĐT vào tên key Redis; không dùng để hash mật khẩu. */
    private static String digest(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    /** Đóng gói Lua script với kiểu trả về Long để StringRedisTemplate thực thi. */
    private static DefaultRedisScript<Long> script(String text) {
        return new DefaultRedisScript<>(text, Long.class);
    }

    /** Quyền xử lý gắn với dữ liệu đăng ký, version và token; không ghi nội dung nhạy cảm khi log. */
    public record Claim(PendingRegistration registration, String version, String token) {
        /** Che dữ liệu đăng ký và token khi đối tượng được chuyển thành chuỗi. */
        @Override public String toString() { return "RegistrationClaim[redacted]"; }
    }
}
