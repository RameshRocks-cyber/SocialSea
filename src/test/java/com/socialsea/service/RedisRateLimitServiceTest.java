package com.socialsea.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisRateLimitService service;

    @BeforeEach
    void setUp() {
        service = new RedisRateLimitService(redisTemplate);
    }

    @Test
    void allowsRequestsWithinCapacity() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(3L);

        Optional<Boolean> result = service.tryConsume("127.0.0.1|/api/auth/login", 10, Duration.ofMinutes(1));

        assertTrue(result.isPresent());
        assertTrue(result.get());
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                anyList(),
                any(Object[].class)
        );
    }

    @Test
    void rejectsRequestsBeyondCapacity() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(11L);

        Optional<Boolean> result = service.tryConsume("127.0.0.1|/api/auth/login", 10, Duration.ofMinutes(1));

        assertTrue(result.isPresent());
        assertFalse(result.get());
    }

    @Test
    void fallsBackWhenRedisIsUnavailable() {
        doThrow(new RuntimeException("redis offline"))
                .when(redisTemplate)
                .execute(any(DefaultRedisScript.class), anyList(), any(Object[].class));

        Optional<Boolean> result = service.tryConsume("127.0.0.1|/api/auth/login", 10, Duration.ofMinutes(1));

        assertTrue(result.isEmpty());
    }
}
