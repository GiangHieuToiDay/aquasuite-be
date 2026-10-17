package com.aquasuite.ims.share.config.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RedisCacheHelper {

    private final RedisTemplate<String, Object> redisTemplate;

    /** Lưu Object vào Redis kèm thời gian tự hủy (TTL) */
    public <T> void set(String key, T value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    /** Lấy Object từ Redis và tự động ép kiểu an toàn sang Class<T> */
    public <T> Optional<T> get(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(clazz.cast(value));
    }

    /** Xóa key khỏi Redis */
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /** Kiểm tra key có tồn tại hay không */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /** Gia hạn thêm TTL cho key đang tồn tại */
    public void expire(String key, Duration ttl) {
        redisTemplate.expire(key, ttl);
    }
}
