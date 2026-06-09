package com.socialsea.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;

@Service
public class RedisRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitService.class);
    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> incrementScript;

    public RedisRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.incrementScript = new DefaultRedisScript<>();
        this.incrementScript.setResultType(Long.class);
        this.incrementScript.setScriptText("""
                local current = redis.call('INCR', KEYS[1])
                if current == 1 then
                    redis.call('EXPIRE', KEYS[1], ARGV[1])
                end
                return current
                """);
    }

    public Optional<Boolean> tryConsume(String rawKey, int capacity, Duration window) {
        if (capacity <= 0 || window == null) {
            return Optional.of(false);
        }

        long ttlSeconds = Math.max(1L, window.getSeconds());
        if (ttlSeconds <= 0L) {
            return Optional.of(false);
        }

        String redisKey = buildKey(rawKey);

        try {
            Long count = redisTemplate.execute(
                    incrementScript,
                    Collections.singletonList(redisKey),
                    String.valueOf(ttlSeconds)
            );

            if (count == null) {
                return Optional.empty();
            }

            return Optional.of(count <= capacity);
        } catch (Exception ex) {
            log.debug("Redis rate limit unavailable for {}", redisKey, ex);
            return Optional.empty();
        }
    }

    private String buildKey(String rawKey) {
        String key = rawKey == null ? "" : rawKey.trim().toLowerCase(Locale.ROOT);
        if (key.isBlank()) {
            key = "unknown";
        }
        if (key.length() > 256) {
            key = key.substring(0, 256);
        }
        return KEY_PREFIX + key;
    }
}
