package com.aquasuite.ims.module.iam.internal.auth.token;

import com.aquasuite.ims.module.iam.internal.error.AuthErrorCode;
import com.aquasuite.ims.share.exception.AppException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSessionStore {

    public static final String PREFIX_SESSION = "auth:v2:session:";
    public static final String PREFIX_REVOKED = "auth:v2:session-revoked:";
    public static final String PREFIX_USED = "auth:v2:refresh-used:";

    public static final int AUTH_GENERATION_V2 = 2;
    public static final int SCHEMA_VERSION_V2 = 2;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${aquasuite.config.auth-generation:2}")
    private int authGeneration = 2;

    private DefaultRedisScript<String> rotateRefreshScript;
    private DefaultRedisScript<String> revokeSessionScript;
    private DefaultRedisScript<String> validateSessionScript;
    private DefaultRedisScript<String> createSessionScript;

    private static final String CREATE_SESSION_LUA =
            "local sessionKey = KEYS[1]\n"
                    + "local value = ARGV[1]\n"
                    + "local pttl = tonumber(ARGV[2])\n"
                    + "if not sessionKey or not value or not pttl or pttl <= 0 then\n"
                    + "    return cjson.encode({ status = 'INCONSISTENT' })\n"
                    + "end\n"
                    + "if redis.call('EXISTS', sessionKey) == 1 then\n"
                    + "    return cjson.encode({ status = 'CONFLICT' })\n"
                    + "end\n"
                    + "local setRes = redis.call('SET', sessionKey, value, 'PX', pttl, 'NX')\n"
                    + "if not setRes then\n"
                    + "    return cjson.encode({ status = 'CONFLICT' })\n"
                    + "end\n"
                    + "return cjson.encode({ status = 'SUCCESS' })\n";

    private static final String VALIDATE_SESSION_LUA =
            "local sessionKey = KEYS[1]\n"
                    + "local revokedKey = KEYS[2]\n"
                    + "local userId = ARGV[1]\n"
                    + "local expectedGen = tonumber(ARGV[2]) or 2\n"
                    + "if redis.call('EXISTS', revokedKey) == 1 then\n"
                    + "    return cjson.encode({ status = 'REVOKED' })\n"
                    + "end\n"
                    + "if redis.call('EXISTS', sessionKey) == 0 then\n"
                    + "    return cjson.encode({ status = 'NOT_FOUND' })\n"
                    + "end\n"
                    + "local sessionType = redis.call('TYPE', sessionKey)\n"
                    + "if not sessionType or sessionType['ok'] ~= 'string' then\n"
                    + "    return cjson.encode({ status = 'INCONSISTENT' })\n"
                    + "end\n"
                    + "local raw = redis.call('GET', sessionKey)\n"
                    + "if not raw then\n"
                    + "    return cjson.encode({ status = 'NOT_FOUND' })\n"
                    + "end\n"
                    + "local sOk, session = pcall(cjson.decode, raw)\n"
                    + "if not sOk or type(session) ~= 'table' then\n"
                    + "    return cjson.encode({ status = 'INCONSISTENT' })\n"
                    + "end\n"
                    + "if tonumber(session.schemaVersion) ~= 2 or tonumber(session.authGeneration) ~= expectedGen then\n"
                    + "    return cjson.encode({ status = 'INCONSISTENT' })\n"
                    + "end\n"
                    + "if session.userId ~= userId then\n"
                    + "    return cjson.encode({ status = 'NOT_FOUND' })\n"
                    + "end\n"
                    + "if session.status ~= 'ACTIVE' then\n"
                    + "    return cjson.encode({ status = 'REVOKED' })\n"
                    + "end\n"
                    + "local pttl = redis.call('PTTL', sessionKey)\n"
                    + "if pttl <= 0 then\n"
                    + "    return cjson.encode({ status = 'EXPIRED' })\n"
                    + "end\n"
                    + "return cjson.encode({ status = 'ACTIVE' })\n";

    @PostConstruct
    public void init() {
        try {
            ClassPathResource rotateResource = new ClassPathResource("redis/rotate-refresh.lua");
            String rotateLua =
                    new String(
                            rotateResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            rotateRefreshScript = new DefaultRedisScript<>(rotateLua, String.class);

            ClassPathResource revokeResource = new ClassPathResource("redis/revoke-session.lua");
            String revokeLua =
                    new String(
                            revokeResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            revokeSessionScript = new DefaultRedisScript<>(revokeLua, String.class);

            validateSessionScript = new DefaultRedisScript<>(VALIDATE_SESSION_LUA, String.class);
            createSessionScript = new DefaultRedisScript<>(CREATE_SESSION_LUA, String.class);
            log.info(
                    "RedisSessionStore initialized successfully with V2 Lua scripts (generation={})",
                    authGeneration);
        } catch (IOException e) {
            log.error("Failed to load Redis Lua scripts", e);
            throw new IllegalStateException("Failed to load Redis Lua scripts from classpath", e);
        }
    }

    public static String buildSessionKey(UUID sid) {
        return PREFIX_SESSION + "{" + sid.toString() + "}";
    }

    public static String buildRevokedKey(UUID sid) {
        return PREFIX_REVOKED + "{" + sid.toString() + "}";
    }

    public static String buildUsedKey(UUID sid, UUID jti) {
        return PREFIX_USED + "{" + sid.toString() + "}:" + jti.toString();
    }

    public enum ValidationState {
        ACTIVE,
        REVOKED,
        EXPIRED,
        NOT_FOUND,
        INCONSISTENT
    }

    public enum RotationOutcome {
        SUCCESS,
        REPLAY,
        REVOKED,
        EXPIRED,
        UNKNOWN_TOKEN,
        INVALID,
        INCONSISTENT,
        UNKNOWN
    }

    public record SessionData(
            UUID userId,
            Instant loginAt,
            Instant absoluteExpiresAt,
            String status,
            UUID currentRefreshJti) {}

    public void createSession(
            UUID sessionId, UUID userId, Instant loginAt, Instant absoluteExpiresAt) {
        createSession(sessionId, userId, UUID.randomUUID(), loginAt, absoluteExpiresAt);
    }

    public void createSession(
            UUID sessionId,
            UUID userId,
            Instant loginAt,
            Instant absoluteExpiresAt,
            String initialJtiStr) {
        UUID initialJti;
        try {
            initialJti = UUID.fromString(initialJtiStr);
        } catch (Exception e) {
            initialJti = UUID.randomUUID();
        }
        createSession(sessionId, userId, initialJti, loginAt, absoluteExpiresAt);
    }

    public void createSession(
            UUID sessionId,
            UUID userId,
            UUID initialRefreshJti,
            Instant loginAt,
            Instant absoluteExpiresAt) {
        String sessionKey = buildSessionKey(sessionId);
        Instant now = Instant.now();
        long ttlMillis = Duration.between(now, absoluteExpiresAt).toMillis();

        if (ttlMillis <= 0) {
            throw new AppException(
                    AuthErrorCode.EXPIRED_REFRESH_TOKEN,
                    "Thời điểm hết hạn tuyệt đối không hợp lệ");
        }

        Map<String, Object> sessionData =
                Map.of(
                        "schemaVersion", SCHEMA_VERSION_V2,
                        "authGeneration", authGeneration,
                        "userId", userId.toString(),
                        "status", "ACTIVE",
                        "currentRefreshJti", initialRefreshJti.toString(),
                        "loginAt", loginAt.toEpochMilli(),
                        "absoluteExpiresAt", absoluteExpiresAt.toEpochMilli());

        try {
            String json = objectMapper.writeValueAsString(sessionData);
            String resultJson =
                    redisTemplate.execute(
                            createSessionScript,
                            List.of(sessionKey),
                            json,
                            String.valueOf(ttlMillis));

            if (resultJson == null) {
                log.warn("Null result returned from createSessionScript for sid: {}", sessionId);
                throw new AppException(
                        AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                        "Dịch vụ xác thực tạm thời không khả dụng");
            }

            JsonNode node = objectMapper.readTree(resultJson);
            String status = node.path("status").asText("INCONSISTENT");
            if (!"SUCCESS".equals(status)) {
                log.error(
                        "Failed to atomically create session for sid: {}, status: {}",
                        sessionId,
                        status);
                throw new AppException(
                        AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                        "Dịch vụ xác thực tạm thời không khả dụng");
            }
            log.debug(
                    "Atomically created Redis V2 session for sid: {}, userId: {}, absoluteExpiresAt: {}",
                    sessionId,
                    userId,
                    absoluteExpiresAt);
        } catch (AppException e) {
            throw e;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize session data for sid: {}", sessionId, e);
            throw new AppException(
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE, "Lỗi xử lý dữ liệu phiên");
        } catch (Exception e) {
            log.error("Redis error while creating session for sid: {}", sessionId, e);
            throw new AppException(
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                    "Dịch vụ xác thực tạm thời không khả dụng");
        }
    }

    public ValidationState validateSession(UUID sessionId, UUID userId) {
        String sessionKey = buildSessionKey(sessionId);
        String revokedKey = buildRevokedKey(sessionId);
        List<String> keys = List.of(sessionKey, revokedKey);
        String[] args = new String[] {userId.toString(), String.valueOf(authGeneration)};

        try {
            String resultJson = redisTemplate.execute(validateSessionScript, keys, (Object[]) args);
            if (resultJson == null) {
                log.warn("Null result returned from validateSessionScript for sid: {}", sessionId);
                throw new AppException(
                        AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                        "Dịch vụ xác thực tạm thời không khả dụng");
            }

            JsonNode node = objectMapper.readTree(resultJson);
            String status = node.path("status").asText("NOT_FOUND");

            return switch (status) {
                case "ACTIVE" -> ValidationState.ACTIVE;
                case "REVOKED" -> ValidationState.REVOKED;
                case "EXPIRED" -> ValidationState.EXPIRED;
                case "INCONSISTENT" ->
                        throw new AppException(
                                AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                                "Trạng thái phiên không nhất quán");
                default -> ValidationState.NOT_FOUND;
            };
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis error while atomically validating session for sid: {}", sessionId, e);
            throw new AppException(
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                    "Dịch vụ xác thực tạm thời không khả dụng");
        }
    }

    public RotationOutcome rotateRefresh(
            UUID sessionId, UUID userId, UUID oldJti, UUID newJti, Instant oldExp, Instant now) {
        String sessionKey = buildSessionKey(sessionId);
        String revokedKey = buildRevokedKey(sessionId);
        String usedOldKey = buildUsedKey(sessionId, oldJti);
        String usedNewKey = buildUsedKey(sessionId, newJti);

        List<String> keys = List.of(sessionKey, revokedKey, usedOldKey, usedNewKey);

        long usedTtl = Math.max(1, Duration.between(now, oldExp).toSeconds());

        String[] args =
                new String[] {
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
            String resultJson = redisTemplate.execute(rotateRefreshScript, keys, (Object[]) args);
            if (resultJson == null) {
                log.warn("Null result returned from rotate script for sid: {}", sessionId);
                throw new AppException(
                        AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                        "Dịch vụ xác thực tạm thời không khả dụng");
            }

            JsonNode node = objectMapper.readTree(resultJson);
            String status = node.path("status").asText("UNKNOWN");
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
        } catch (RedisSystemException e) {
            log.error("Redis system error during rotate-refresh for sid: {}", sessionId, e);
            throw new AppException(
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                    "Dịch vụ xác thực tạm thời không khả dụng");
        } catch (Exception e) {
            if (e instanceof AppException) {
                throw (AppException) e;
            }
            log.error("Error executing rotate-refresh script for sid: {}", sessionId, e);
            throw new AppException(
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                    "Dịch vụ xác thực tạm thời không khả dụng");
        }
    }

    public void revokeSession(UUID sessionId, UUID userId, String reason) {
        String sessionKey = buildSessionKey(sessionId);
        String revokedKey = buildRevokedKey(sessionId);

        List<String> keys = List.of(sessionKey, revokedKey);
        String[] args =
                new String[] {
                    userId.toString(),
                    sessionId.toString(),
                    reason != null ? reason : "LOGOUT",
                    String.valueOf(Instant.now().toEpochMilli()),
                    "1296000", // 15 days default TTL upper bound
                    String.valueOf(authGeneration)
                };

        try {
            String resultJson = redisTemplate.execute(revokeSessionScript, keys, (Object[]) args);
            if (resultJson == null) {
                log.error("Null result returned from revokeSessionScript for sid: {}", sessionId);
                throw new AppException(
                        AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                        "Dịch vụ xác thực tạm thời không khả dụng");
            }
            JsonNode node = objectMapper.readTree(resultJson);
            String status = node.path("status").asText("INCONSISTENT");
            if (!"SUCCESS".equals(status)) {
                log.warn(
                        "Revoke script returned non-success status: {} for sid: {}",
                        status,
                        sessionId);
                throw new AppException(
                        AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                        "Dịch vụ xác thực tạm thời không khả dụng");
            }
            log.info("Revoked session sid: {}, userId: {}, reason: {}", sessionId, userId, reason);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis error while revoking session for sid: {}", sessionId, e);
            throw new AppException(
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                    "Dịch vụ xác thực tạm thời không khả dụng");
        }
    }

    public boolean isRevoked(UUID sessionId) {
        try {
            String raw = redisTemplate.opsForValue().get(buildRevokedKey(sessionId));
            if (raw == null) {
                return false;
            }
            JsonNode node = objectMapper.readTree(raw);
            return node.has("userId")
                    && node.has("status")
                    && "REVOKED".equals(node.path("status").asText());
        } catch (Exception e) {
            log.error("Redis error checking revoked state for sid: {}", sessionId, e);
            throw new AppException(
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                    "Dịch vụ xác thực tạm thời không khả dụng");
        }
    }

    public SessionData getSessionData(UUID sessionId) {
        try {
            String raw = redisTemplate.opsForValue().get(buildSessionKey(sessionId));
            if (raw == null) {
                return null;
            }
            JsonNode node = objectMapper.readTree(raw);
            if (node.path("schemaVersion").asInt() != 2
                    || node.path("authGeneration").asInt() != authGeneration) {
                log.warn(
                        "Session sid: {} has incompatible schemaVersion or authGeneration",
                        sessionId);
                return null;
            }
            return new SessionData(
                    UUID.fromString(node.path("userId").asText()),
                    Instant.ofEpochMilli(node.path("loginAt").asLong()),
                    Instant.ofEpochMilli(node.path("absoluteExpiresAt").asLong()),
                    node.path("status").asText(),
                    UUID.fromString(node.path("currentRefreshJti").asText()));
        } catch (Exception e) {
            log.error("Redis error reading session data for sid: {}", sessionId, e);
            throw new AppException(
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                    "Dịch vụ xác thực tạm thời không khả dụng");
        }
    }

    public String getRawSession(UUID sessionId) {
        try {
            return redisTemplate.opsForValue().get(buildSessionKey(sessionId));
        } catch (Exception e) {
            log.error("Redis error reading raw session for sid: {}", sessionId, e);
            throw new AppException(
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                    "Dịch vụ xác thực tạm thời không khả dụng");
        }
    }

    public String getRawRevoked(UUID sessionId) {
        try {
            return redisTemplate.opsForValue().get(buildRevokedKey(sessionId));
        } catch (Exception e) {
            log.error("Redis error reading raw revoked marker for sid: {}", sessionId, e);
            throw new AppException(
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                    "Dịch vụ xác thực tạm thời không khả dụng");
        }
    }

    public String getRawUsed(UUID sessionId, UUID jti) {
        try {
            return redisTemplate.opsForValue().get(buildUsedKey(sessionId, jti));
        } catch (Exception e) {
            log.error(
                    "Redis error reading raw used marker for sid: {}, jti: {}", sessionId, jti, e);
            throw new AppException(
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                    "Dịch vụ xác thực tạm thời không khả dụng");
        }
    }

    public Long getSessionTtl(UUID sessionId) {
        try {
            return redisTemplate.getExpire(buildSessionKey(sessionId));
        } catch (Exception e) {
            log.error("Redis error reading session TTL for sid: {}", sessionId, e);
            throw new AppException(
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                    "Dịch vụ xác thực tạm thời không khả dụng");
        }
    }

    public Long getRevokedTtl(UUID sessionId) {
        try {
            return redisTemplate.getExpire(buildRevokedKey(sessionId));
        } catch (Exception e) {
            log.error("Redis error reading revoked TTL for sid: {}", sessionId, e);
            throw new AppException(
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                    "Dịch vụ xác thực tạm thời không khả dụng");
        }
    }

    public Long getUsedTtl(UUID sessionId, UUID jti) {
        try {
            return redisTemplate.getExpire(buildUsedKey(sessionId, jti));
        } catch (Exception e) {
            log.error("Redis error reading used TTL for sid: {}, jti: {}", sessionId, jti, e);
            throw new AppException(
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                    "Dịch vụ xác thực tạm thời không khả dụng");
        }
    }
}
