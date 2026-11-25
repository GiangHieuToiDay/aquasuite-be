package com.aquasuite.ims.repository.iam.store;

import com.aquasuite.ims.exception.iam.AuthErrorCode;
import com.aquasuite.ims.exception.AppException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Kho phiên đăng nhập lưu trên Redis (cơ chế V2): tạo phiên, kiểm tra phiên còn hiệu lực, xoay vòng
 * refresh token có phát hiện dùng lại (replay) và thu hồi phiên. Các thao tác cần tính nguyên tử được
 * viết bằng script Lua trong thư mục resources/redis nên an toàn khi nhiều request chạy đồng thời.
 * Mọi lỗi Redis đều được quy về lỗi AUTH_SERVICE_UNAVAILABLE.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSessionStore {

    /** Tiền tố key chứa dữ liệu phiên đang hoạt động. */
    public static final String PREFIX_SESSION = "auth:v2:session:";
    /** Tiền tố key đánh dấu phiên đã bị thu hồi. */
    public static final String PREFIX_REVOKED = "auth:v2:session-revoked:";
    /** Tiền tố key đánh dấu refresh token đã dùng, để phát hiện dùng lại. */
    public static final String PREFIX_USED = "auth:v2:refresh-used:";
    /** Tiền tố key danh sách các phiên của một người dùng. */
    public static final String PREFIX_USER_SESSIONS = "auth:v2:user-sessions:";

    public static final int AUTH_GENERATION_V2 = 2;
    public static final int SCHEMA_VERSION_V2 = 2;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** Thế hệ xác thực hiện hành; phiên khác thế hệ bị coi là không tương thích. */
    @Value("${aquasuite.config.auth-generation:2}")
    private int authGeneration = 2;

    private DefaultRedisScript<String> rotateRefreshScript;
    private DefaultRedisScript<String> revokeSessionScript;
    private DefaultRedisScript<String> validateSessionScript;
    private DefaultRedisScript<String> createSessionScript;

    /** Nạp bốn script Lua (tạo, kiểm tra, xoay vòng, thu hồi) từ classpath khi khởi động. */
    @PostConstruct
    public void init() {
        try {
            rotateRefreshScript = loadScript("redis/rotate-refresh.lua");
            revokeSessionScript = loadScript("redis/revoke-session.lua");
            validateSessionScript = loadScript("redis/validate-session.lua");
            createSessionScript = loadScript("redis/create-session.lua");
            log.info("RedisSessionStore initialized successfully with V2 Lua scripts (generation={})", authGeneration);
        } catch (IOException e) {
            log.error("Failed to load Redis Lua scripts", e);
            throw new IllegalStateException("Failed to load Redis Lua scripts from classpath", e);
        }
    }

    /** Đọc một file Lua trong classpath thành script Redis trả về chuỗi JSON. */
    private static DefaultRedisScript<String> loadScript(String classpathLocation) throws IOException {
        String lua = new String(
                new ClassPathResource(classpathLocation).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new DefaultRedisScript<>(lua, String.class);
    }

    /** Tạo lỗi dịch vụ xác thực tạm thời không khả dụng. */
    private static AppException unavailable() {
        return new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE, "Dịch vụ xác thực tạm thời không khả dụng");
    }

    /** Chạy một script Lua và trả về trường {@code status} trong JSON kết quả. */
    private String runScript(
            DefaultRedisScript<String> script, List<String> keys, UUID sessionId, String defaultStatus, Object... args) {
        String resultJson = redisTemplate.execute(script, keys, args);
        if (resultJson == null) {
            log.warn("Null result returned from Redis Lua script for sid: {}", sessionId);
            throw unavailable();
        }
        try {
            return objectMapper.readTree(resultJson).path("status").asText(defaultStatus);
        } catch (JsonProcessingException e) {
            log.error("Unreadable Lua result for sid: {}", sessionId, e);
            throw unavailable();
        }
    }

    /** Đọc một giá trị từ Redis, mọi lỗi Redis được quy về AUTH_SERVICE_UNAVAILABLE. */
    private <T> T redisRead(String what, Supplier<T> read) {
        try {
            return read.get();
        } catch (Exception e) {
            log.error("Redis error {}", what, e);
            throw unavailable();
        }
    }

    /** Key dữ liệu của một phiên; dấu ngoặc nhọn giữ các key của cùng phiên trên cùng slot Redis Cluster. */
    public static String buildSessionKey(UUID sid) {
        return PREFIX_SESSION + "{" + sid.toString() + "}";
    }

    /** Key đánh dấu phiên đã bị thu hồi. */
    public static String buildRevokedKey(UUID sid) {
        return PREFIX_REVOKED + "{" + sid.toString() + "}";
    }

    /** Key đánh dấu một refresh token (theo jti) của phiên đã được dùng. */
    public static String buildUsedKey(UUID sid, UUID jti) {
        return PREFIX_USED + "{" + sid.toString() + "}:" + jti.toString();
    }

    /** Key tập hợp ID các phiên của một người dùng. */
    public static String buildUserSessionsKey(UUID userId) {
        return PREFIX_USER_SESSIONS + "{" + userId.toString() + "}";
    }

    /** Kết quả kiểm tra một phiên trong Redis. */
    public enum ValidationState {
        /** Phiên còn hiệu lực. */
        ACTIVE,
        /** Phiên đã bị thu hồi. */
        REVOKED,
        /** Phiên đã hết hạn. */
        EXPIRED,
        /** Không tìm thấy phiên của người dùng này. */
        NOT_FOUND,
        /** Dữ liệu phiên sai cấu trúc hoặc sai thế hệ. */
        INCONSISTENT
    }

    /** Kết quả xoay vòng refresh token. */
    public enum RotationOutcome {
        /** Xoay vòng thành công. */
        SUCCESS,
        /** Refresh token đã dùng rồi (nghi bị đánh cắp), phiên bị thu hồi. */
        REPLAY,
        /** Phiên đã bị thu hồi. */
        REVOKED,
        /** Phiên hoặc token đã hết hạn. */
        EXPIRED,
        /** Token không thuộc phiên nào đang biết. */
        UNKNOWN_TOKEN,
        /** Dữ liệu yêu cầu không hợp lệ. */
        INVALID,
        /** Trạng thái Redis không nhất quán. */
        INCONSISTENT,
        /** Trạng thái không xác định. */
        UNKNOWN
    }

    /** Dữ liệu một phiên đọc từ Redis. */
    public record SessionData(
            UUID userId,
            Instant loginAt,
            Instant absoluteExpiresAt,
            String status,
            UUID currentRefreshJti
    ) {}

    /** Tạo phiên mới với jti của refresh token đầu tiên được sinh ngẫu nhiên. */
    public void createSession(UUID sessionId, UUID userId, Instant loginAt, Instant absoluteExpiresAt) {
        createSession(sessionId, userId, UUID.randomUUID(), loginAt, absoluteExpiresAt);
    }

    /** Tạo phiên mới với jti ban đầu dạng chuỗi; chuỗi không phải UUID thì dùng UUID ngẫu nhiên. */
    public void createSession(UUID sessionId, UUID userId, Instant loginAt, Instant absoluteExpiresAt, String initialJtiStr) {
        UUID initialJti;
        try {
            initialJti = UUID.fromString(initialJtiStr);
        } catch (Exception e) {
            initialJti = UUID.randomUUID();
        }
        createSession(sessionId, userId, initialJti, loginAt, absoluteExpiresAt);
    }

    /**
     * Ghi phiên mới vào Redis một cách nguyên tử với thời sống bằng thời gian còn lại tới hạn tuyệt đối.
     * Ném lỗi nếu hạn tuyệt đối đã qua, phiên đã tồn tại hoặc Redis không khả dụng.
     */
    public void createSession(UUID sessionId, UUID userId, UUID initialRefreshJti, Instant loginAt, Instant absoluteExpiresAt) {
        String sessionKey = buildSessionKey(sessionId);
        // Thời sống của khóa Redis bằng thời gian còn lại tới hạn tuyệt đối của phiên
        Instant now = Instant.now();
        long ttlMillis = Duration.between(now, absoluteExpiresAt).toMillis();

        if (ttlMillis <= 0) {
            throw new AppException(AuthErrorCode.EXPIRED_REFRESH_TOKEN, "Thời điểm hết hạn tuyệt đối không hợp lệ");
        }

        Map<String, Object> sessionData = Map.of(
                "schemaVersion", SCHEMA_VERSION_V2,
                "authGeneration", authGeneration,
                "userId", userId.toString(),
                "status", "ACTIVE",
                "currentRefreshJti", initialRefreshJti.toString(),
                "loginAt", loginAt.toEpochMilli(),
                "absoluteExpiresAt", absoluteExpiresAt.toEpochMilli()
        );

        try {
            String status = runScript(
                    createSessionScript,
                    List.of(sessionKey, buildUserSessionsKey(userId)),
                    sessionId,
                    "INCONSISTENT",
                    objectMapper.writeValueAsString(sessionData),
                    String.valueOf(ttlMillis),
                    sessionId.toString());
            if (!"SUCCESS".equals(status)) {
                log.error("Failed to atomically create session for sid: {}, status: {}", sessionId, status);
                throw unavailable();
            }
            log.debug("Atomically created Redis V2 session for sid: {}, userId: {}, absoluteExpiresAt: {}", sessionId, userId, absoluteExpiresAt);
        } catch (AppException e) {
            throw e;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize session data for sid: {}", sessionId, e);
            throw new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE, "Lỗi xử lý dữ liệu phiên");
        } catch (Exception e) {
            log.error("Redis error while creating session for sid: {}", sessionId, e);
            throw unavailable();
        }
    }

    /** Kiểm tra nguyên tử phiên có thuộc người dùng và còn ACTIVE hay không; trạng thái không nhất quán ném lỗi. */
    public ValidationState validateSession(UUID sessionId, UUID userId) {
        List<String> keys = List.of(
                buildSessionKey(sessionId), buildRevokedKey(sessionId), buildUserSessionsKey(userId));
        try {
            String status = runScript(
                    validateSessionScript, keys, sessionId, "NOT_FOUND",
                    userId.toString(), String.valueOf(authGeneration));
            return switch (status) {
                case "ACTIVE" -> ValidationState.ACTIVE;
                case "REVOKED" -> ValidationState.REVOKED;
                case "EXPIRED" -> ValidationState.EXPIRED;
                case "INCONSISTENT" -> throw new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE, "Trạng thái phiên không nhất quán");
                default -> ValidationState.NOT_FOUND;
            };
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis error while atomically validating session for sid: {}", sessionId, e);
            throw unavailable();
        }
    }

    /**
     * Xoay vòng refresh token: đánh dấu jti cũ đã dùng và thay bằng jti mới, tất cả trong một script Lua.
     * Dùng lại jti cũ bị coi là replay và thu hồi cả phiên.
     */
    public RotationOutcome rotateRefresh(
            UUID sessionId,
            UUID userId,
            UUID oldJti,
            UUID newJti,
            Instant oldExp,
            Instant now
    ) {
        String sessionKey = buildSessionKey(sessionId);
        String revokedKey = buildRevokedKey(sessionId);
        String usedOldKey = buildUsedKey(sessionId, oldJti);
        String usedNewKey = buildUsedKey(sessionId, newJti);

        List<String> keys = List.of(sessionKey, revokedKey, usedOldKey, usedNewKey);

        long usedTtl = Math.max(1, Duration.between(now, oldExp).toSeconds());

        String[] args = new String[]{
                userId.toString(),
                sessionId.toString(),
                oldJti.toString(),
                newJti.toString(),
                String.valueOf(oldExp.toEpochMilli()),
                String.valueOf(now.toEpochMilli()),
                String.valueOf(usedTtl),
                String.valueOf(authGeneration)
        };

        try {
            String status = runScript(rotateRefreshScript, keys, sessionId, "UNKNOWN", (Object[]) args);
            log.debug("Rotate script returned status: {} for sid: {}", status, sessionId);

            return switch (status) {
                case "SUCCESS" -> RotationOutcome.SUCCESS;
                case "REPLAY" -> RotationOutcome.REPLAY;
                case "REVOKED" -> RotationOutcome.REVOKED;
                case "EXPIRED" -> RotationOutcome.EXPIRED;
                case "UNKNOWN_TOKEN" -> RotationOutcome.UNKNOWN_TOKEN;
                case "INVALID" -> RotationOutcome.INVALID;
                case "INCONSISTENT" -> RotationOutcome.INCONSISTENT;
                default -> RotationOutcome.UNKNOWN;
            };
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error executing rotate-refresh script for sid: {}", sessionId, e);
            throw unavailable();
        }
    }

    /** Thu hồi một phiên (ví dụ khi đăng xuất) và ghi lại lý do. */
    public void revokeSession(UUID sessionId, UUID userId, String reason) {
        String sessionKey = buildSessionKey(sessionId);
        String revokedKey = buildRevokedKey(sessionId);

        List<String> keys = List.of(sessionKey, revokedKey, buildUserSessionsKey(userId));
        String[] args = new String[]{
                userId.toString(),
                sessionId.toString(),
                reason != null ? reason : "LOGOUT",
                String.valueOf(Instant.now().toEpochMilli()),
                "1296000", // TTL tối đa của dấu thu hồi: 15 ngày
                String.valueOf(authGeneration)
        };

        try {
            String status = runScript(revokeSessionScript, keys, sessionId, "INCONSISTENT", (Object[]) args);
            if (!"SUCCESS".equals(status)) {
                log.warn("Revoke script returned non-success status: {} for sid: {}", status, sessionId);
                throw unavailable();
            }
            log.info("Revoked session sid: {}, userId: {}, reason: {}", sessionId, userId, reason);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis error while revoking session for sid: {}", sessionId, e);
            throw unavailable();
        }
    }

    /** Thu hồi mọi phiên đang theo dõi của một người dùng, dùng khi trạng thái tài khoản thay đổi. */
    public void revokeAllSessions(UUID userId, String reason) {
        String userSessionsKey = buildUserSessionsKey(userId);
        try {
            Set<String> sessionIds = redisTemplate.opsForSet().members(userSessionsKey);
            if (sessionIds == null || sessionIds.isEmpty()) {
                return;
            }
            for (String rawSessionId : sessionIds) {
                try {
                    UUID sessionId = UUID.fromString(rawSessionId);
                    SessionData session = getSessionData(sessionId);
                    if (session != null && userId.equals(session.userId())) {
                        revokeSession(sessionId, userId, reason);
                    } else {
                        redisTemplate.opsForSet().remove(userSessionsKey, rawSessionId);
                    }
                } catch (IllegalArgumentException ignored) {
                    redisTemplate.opsForSet().remove(userSessionsKey, rawSessionId);
                }
            }
            redisTemplate.delete(userSessionsKey);
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Redis error while revoking sessions for userId={}", userId, exception);
            throw unavailable();
        }
    }

    /** Cho biết phiên đã bị đánh dấu thu hồi hay chưa. */
    public boolean isRevoked(UUID sessionId) {
        try {
            String raw = redisTemplate.opsForValue().get(buildRevokedKey(sessionId));
            if (raw == null) {
                return false;
            }
            JsonNode node = objectMapper.readTree(raw);
            return node.has("userId") && node.has("status") && "REVOKED".equals(node.path("status").asText());
        } catch (Exception e) {
            log.error("Redis error checking revoked state for sid: {}", sessionId, e);
            throw unavailable();
        }
    }

    /** Đọc dữ liệu phiên; trả null nếu không có hoặc phiên sai thế hệ/cấu trúc. */
    public SessionData getSessionData(UUID sessionId) {
        try {
            String raw = redisTemplate.opsForValue().get(buildSessionKey(sessionId));
            if (raw == null) {
                return null;
            }
            JsonNode node = objectMapper.readTree(raw);
            if (node.path("schemaVersion").asInt() != 2 || node.path("authGeneration").asInt() != authGeneration) {
                log.warn("Session sid: {} has incompatible schemaVersion or authGeneration", sessionId);
                return null;
            }
            return new SessionData(
                    UUID.fromString(node.path("userId").asText()),
                    Instant.ofEpochMilli(node.path("loginAt").asLong()),
                    Instant.ofEpochMilli(node.path("absoluteExpiresAt").asLong()),
                    node.path("status").asText(),
                    UUID.fromString(node.path("currentRefreshJti").asText())
            );
        } catch (Exception e) {
            log.error("Redis error reading session data for sid: {}", sessionId, e);
            throw unavailable();
        }
    }

    /** Đọc nguyên văn dữ liệu phiên (phục vụ kiểm thử và chẩn đoán). */
    public String getRawSession(UUID sessionId) {
        return redisRead("reading raw session for sid " + sessionId,
                () -> redisTemplate.opsForValue().get(buildSessionKey(sessionId)));
    }

    /** Đọc nguyên văn dấu thu hồi của phiên. */
    public String getRawRevoked(UUID sessionId) {
        return redisRead("reading raw revoked marker for sid " + sessionId,
                () -> redisTemplate.opsForValue().get(buildRevokedKey(sessionId)));
    }

    /** Đọc nguyên văn dấu "đã dùng" của một refresh token. */
    public String getRawUsed(UUID sessionId, UUID jti) {
        return redisRead("reading raw used marker for sid " + sessionId + ", jti " + jti,
                () -> redisTemplate.opsForValue().get(buildUsedKey(sessionId, jti)));
    }

    /** Thời gian sống còn lại (giây) của key phiên. */
    public Long getSessionTtl(UUID sessionId) {
        return redisRead("reading session TTL for sid " + sessionId,
                () -> redisTemplate.getExpire(buildSessionKey(sessionId)));
    }

    /** Thời gian sống còn lại (giây) của dấu thu hồi. */
    public Long getRevokedTtl(UUID sessionId) {
        return redisRead("reading revoked TTL for sid " + sessionId,
                () -> redisTemplate.getExpire(buildRevokedKey(sessionId)));
    }

    /** Thời gian sống còn lại (giây) của dấu "đã dùng" của refresh token. */
    public Long getUsedTtl(UUID sessionId, UUID jti) {
        return redisRead("reading used TTL for sid " + sessionId + ", jti " + jti,
                () -> redisTemplate.getExpire(buildUsedKey(sessionId, jti)));
    }
}
