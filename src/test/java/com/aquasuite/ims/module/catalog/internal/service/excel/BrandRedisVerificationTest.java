package com.aquasuite.ims.module.catalog.internal.service.excel;

import com.aquasuite.ims.share.config.redis.RedisConfig;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BrandRedisVerificationTest {
    @Test void serializerAndTtlRoundTrip() {
        try (GenericContainer<?> redis = new GenericContainer<>("redis:7.2-alpine").withExposedPorts(6379)) {
            redis.start();
            LettuceConnectionFactory factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            factory.afterPropertiesSet();
            try {
                var template = new RedisConfig().redisTemplate(factory);
                var job = new BrandImportJob(UUID.randomUUID(), UUID.randomUUID());
                job.finishedAt = Instant.parse("2025-01-01T00:00:00Z");
                job.rows.add(new BrandImportJob.BrandImportRow(2, "ABC", "SKIPPED", "DB_DUPLICATE_CODE", null, null));
                template.opsForValue().set("verification", job, Duration.ofHours(1));
                var copy = (BrandImportJob) template.opsForValue().get("verification");
                assertEquals(job.jobId, copy.jobId);
                assertEquals(job.ownerId, copy.ownerId);
                assertEquals(job.finishedAt, copy.finishedAt);
                assertEquals(job.rows, copy.rows);
                Long ttl = template.getExpire("verification");
                assertNotNull(ttl);
                assertTrue(ttl > 3500 && ttl <= 3600);
            } finally { factory.destroy(); }
        }
    }
}
