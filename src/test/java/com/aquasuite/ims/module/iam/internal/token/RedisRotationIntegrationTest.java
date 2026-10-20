package com.aquasuite.ims.module.iam.internal.token;
import com.aquasuite.ims.testsupport.AuthIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisRotationIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired
    private RedisSessionStore redisSessionStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        Set<String> keys = redisTemplate.keys("auth:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("V2 Atomic Rotation: Xoay token thành công và ghi nhận used marker")
    void shouldRotateSuccessfully() {
        UUID sid = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID jti1 = UUID.randomUUID();
        UUID jti2 = UUID.randomUUID();

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant absExp = now.plusSeconds(3600);
        Instant oldExp = now.plusSeconds(1800);

        redisSessionStore.createSession(sid, userId, jti1, now, absExp);

        RedisSessionStore.RotationOutcome outcome = redisSessionStore.rotateRefresh(sid, userId, jti1, jti2, oldExp, now);
        assertThat(outcome).isEqualTo(RedisSessionStore.RotationOutcome.SUCCESS);

        RedisSessionStore.SessionData session = redisSessionStore.getSessionData(sid);
        assertThat(session).isNotNull();
        assertThat(session.currentRefreshJti()).isEqualTo(jti2);
        assertThat(session.status()).isEqualTo("ACTIVE");

        // Used marker must be present
        String rawUsed = redisSessionStore.getRawUsed(sid, jti1);
        assertThat(rawUsed).isNotNull();
        assertThat(rawUsed).contains("CONSUMED");
    }

    @Test
    @DisplayName("V2 Replay Detection: Tái sử dụng refresh token cũ thu hồi toàn bộ session")
    void shouldDetectReplayAndRevokeWholeSession() {
        UUID sid = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID jti1 = UUID.randomUUID();
        UUID jti2 = UUID.randomUUID();
        UUID jti3 = UUID.randomUUID();

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant absExp = now.plusSeconds(3600);
        Instant oldExp = now.plusSeconds(1800);

        redisSessionStore.createSession(sid, userId, jti1, now, absExp);

        // First rotation succeeds
        RedisSessionStore.RotationOutcome outcome1 = redisSessionStore.rotateRefresh(sid, userId, jti1, jti2, oldExp, now);
        assertThat(outcome1).isEqualTo(RedisSessionStore.RotationOutcome.SUCCESS);

        // Replaying consumed jti1 triggers REPLAY
        RedisSessionStore.RotationOutcome outcome2 = redisSessionStore.rotateRefresh(sid, userId, jti1, jti3, oldExp, now);
        assertThat(outcome2).isEqualTo(RedisSessionStore.RotationOutcome.REPLAY);

        // Session must be deleted
        assertThat(redisSessionStore.getSessionData(sid)).isNull();

        // Revoked tombstone must exist
        assertThat(redisSessionStore.isRevoked(sid)).isTrue();
        String rawRevoked = redisSessionStore.getRawRevoked(sid);
        assertThat(rawRevoked).contains("REPLAY");

        // Subsequent validation returns REVOKED
        RedisSessionStore.ValidationState valState = redisSessionStore.validateSession(sid, userId);
        assertThat(valState).isEqualTo(RedisSessionStore.ValidationState.REVOKED);
    }

    @Test
    @DisplayName("V2 Unknown Token: Token không khớp current và chưa từng tiêu thụ bị từ chối UNKNOWN_TOKEN")
    void shouldRejectUnknownToken() {
        UUID sid = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID jti1 = UUID.randomUUID();
        UUID unknownJti = UUID.randomUUID();
        UUID newJti = UUID.randomUUID();

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant absExp = now.plusSeconds(3600);
        Instant oldExp = now.plusSeconds(1800);

        redisSessionStore.createSession(sid, userId, jti1, now, absExp);

        RedisSessionStore.RotationOutcome outcome = redisSessionStore.rotateRefresh(sid, userId, unknownJti, newJti, oldExp, now);
        assertThat(outcome).isEqualTo(RedisSessionStore.RotationOutcome.UNKNOWN_TOKEN);

        // Original session is untouched
        RedisSessionStore.SessionData session = redisSessionStore.getSessionData(sid);
        assertThat(session).isNotNull();
        assertThat(session.currentRefreshJti()).isEqualTo(jti1);
    }

    @Test
    @DisplayName("V2 Concurrency: Đua lệnh refresh chỉ có tối đa 1 thành công")
    void shouldHandleConcurrentRotationWithOnlyOneSuccess() throws Exception {
        UUID sid = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID jti1 = UUID.randomUUID();

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant absExp = now.plusSeconds(3600);
        Instant oldExp = now.plusSeconds(1800);

        redisSessionStore.createSession(sid, userId, jti1, now, absExp);

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger replayOrRevokedCount = new AtomicInteger(0);

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();

                UUID candidateNewJti = UUID.randomUUID();
                RedisSessionStore.RotationOutcome outcome = redisSessionStore.rotateRefresh(
                        sid, userId, jti1, candidateNewJti, oldExp, Instant.now()
                );
                if (outcome == RedisSessionStore.RotationOutcome.SUCCESS) {
                    successCount.incrementAndGet();
                } else if (outcome == RedisSessionStore.RotationOutcome.REPLAY || outcome == RedisSessionStore.RotationOutcome.REVOKED) {
                    replayOrRevokedCount.incrementAndGet();
                }
                return null;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Void> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Exactly one concurrent rotation succeeds
        assertThat(successCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("V2 Logout: Thu hồi phiên ngay lập tức và ngăn cản refresh tiếp tục")
    void shouldRevokeSessionImmediatelyOnLogout() {
        UUID sid = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID jti1 = UUID.randomUUID();
        UUID jti2 = UUID.randomUUID();

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant absExp = now.plusSeconds(3600);
        Instant oldExp = now.plusSeconds(1800);

        redisSessionStore.createSession(sid, userId, jti1, now, absExp);

        // Perform logout
        redisSessionStore.revokeSession(sid, userId, "LOGOUT");

        // Validate session returns REVOKED
        assertThat(redisSessionStore.validateSession(sid, userId)).isEqualTo(RedisSessionStore.ValidationState.REVOKED);

        // Attempting rotateRefresh on revoked session returns REVOKED
        RedisSessionStore.RotationOutcome outcome = redisSessionStore.rotateRefresh(sid, userId, jti1, jti2, oldExp, now);
        assertThat(outcome).isEqualTo(RedisSessionStore.RotationOutcome.REVOKED);
    }
}
