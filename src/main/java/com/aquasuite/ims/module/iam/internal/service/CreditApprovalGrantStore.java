package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.internal.error.CreditApprovalErrorCode;
import com.aquasuite.ims.share.exception.AppException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Redis-backed, atomically consumed approval grant; no credential is ever stored here. */
@Component
@RequiredArgsConstructor
public class CreditApprovalGrantStore {
    private static final String PREFIX = "iam:credit-approval:";
    private static final DefaultRedisScript<String> GET_AND_DELETE = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); if value then redis.call('DEL', KEYS[1]); end; return value", String.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void put(String token, GrantData grantData, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(PREFIX + token, objectMapper.writeValueAsString(grantData), ttl);
        } catch (JsonProcessingException exception) {
            throw new AppException(CreditApprovalErrorCode.GRANT_STORE_UNAVAILABLE);
        } catch (RuntimeException exception) {
            throw new AppException(CreditApprovalErrorCode.GRANT_STORE_UNAVAILABLE);
        }
    }

    public GrantData consume(String token) {
        if (token == null || token.isBlank()) {
            throw new AppException(CreditApprovalErrorCode.INVALID_GRANT);
        }
        try {
            String value = redisTemplate.execute(GET_AND_DELETE, List.of(PREFIX + token));
            if (value == null) {
                throw new AppException(CreditApprovalErrorCode.INVALID_GRANT);
            }
            return objectMapper.readValue(value, GrantData.class);
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new AppException(CreditApprovalErrorCode.GRANT_STORE_UNAVAILABLE);
        }
    }

    public record GrantData(
            UUID approverId,
            UUID requesterId,
            UUID customerId,
            BigDecimal creditLimit,
            Integer creditTermDays,
            Instant expiresAt) {}
}
