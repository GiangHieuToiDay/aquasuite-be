package com.aquasuite.ims.module.catalog.internal.service.excel;

import com.aquasuite.ims.share.config.redis.RedisConfig;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import java.time.Duration;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CategoryRedisVerificationTest {
    @Test void serializerAndTtlRoundTrip() {
        try (GenericContainer<?> redis = new GenericContainer<>("redis:7.2-alpine").withExposedPorts(6379)) {
            redis.start();
            LettuceConnectionFactory factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            factory.afterPropertiesSet();
            try {
                var template = new RedisConfig().redisTemplate(factory);
                var job = new CategoryImportJob(UUID.randomUUID(), UUID.randomUUID());
                job.rows.add(new CategoryImportJob.CategoryImportRow(2, "CAT", "PARENT", "SKIPPED", "DB_DUPLICATE_CODE", null, null));
                template.opsForValue().set("verification", job, Duration.ofHours(1));
                var copy = (CategoryImportJob) template.opsForValue().get("verification");
                assertEquals(job.ownerId, copy.ownerId);
                assertEquals(job.rows, copy.rows);
                Long ttl = template.getExpire("verification");
                assertNotNull(ttl);
                assertTrue(ttl > 3500 && ttl <= 3600);
            } finally { factory.destroy(); }
        }
    }
}
