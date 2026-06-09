package com.socialsea.service;

import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceServiceRedisTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    @SuppressWarnings("rawtypes")
    private ValueOperations valueOps;

    @Mock
    @SuppressWarnings("rawtypes")
    private SetOperations setOps;

    @Mock
    private StringRedisTemplate redisTemplate;

    private PresenceService presenceService;

    @BeforeEach
    void setUp() {
        presenceService = new PresenceService(userRepo);
        presenceService.redisTemplate = redisTemplate;
        presenceService.onlineTtl = Duration.ofMinutes(5);
        presenceService.sessionTtl = Duration.ofHours(12);
        presenceService.lastSeenTtl = Duration.ofDays(7);
        presenceService.persistThrottle = Duration.ofSeconds(15);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
    }

    @Test
    @SuppressWarnings("unchecked")
    void markConnectedStoresSocketPresenceInRedis() {
        User user = new User();
        user.setEmail("user@example.com");
        when(userRepo.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(valueOps.get("presence:session:sid-1")).thenReturn(null);

        presenceService.markConnected("user@example.com", "sid-1");

        verify(valueOps).set("presence:session:sid-1", "user@example.com", Duration.ofHours(12));
        verify(setOps).add("presence:user:sessions:user@example.com", "sid-1");
        verify(redisTemplate).expire("presence:user:sessions:user@example.com", Duration.ofHours(12));

        ArgumentCaptor<String> lastSeenCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("presence:last-seen:user@example.com"), lastSeenCaptor.capture(), eq(Duration.ofDays(7)));
        assertNotNull(lastSeenCaptor.getValue());
        verify(userRepo).save(any(User.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void isOnlineUsesRedisSessionStateFirst() {
        User user = new User();
        user.setEmail("user@example.com");
        when(setOps.members("presence:user:sessions:user@example.com")).thenReturn(Set.of("sid-1"));
        when(redisTemplate.hasKey("presence:session:sid-1")).thenReturn(true);

        assertTrue(presenceService.isOnline(user));
        verify(userRepo, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void markDisconnectedRemovesSocketPresenceFromRedis() {
        User user = new User();
        user.setEmail("user@example.com");
        when(userRepo.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(valueOps.get("presence:session:sid-1")).thenReturn(null);
        when(setOps.size("presence:user:sessions:user@example.com")).thenReturn(0L);

        presenceService.markConnected("user@example.com", "sid-1");
        presenceService.markDisconnected("sid-1");

        verify(setOps).remove("presence:user:sessions:user@example.com", "sid-1");
        verify(redisTemplate).delete("presence:session:sid-1");
        verify(redisTemplate).delete("presence:user:sessions:user@example.com");
    }

    @Test
    void isOnlineFallsBackToRecentDatabasePresenceWhenRedisHasNoSession() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setPresenceUpdatedAt(LocalDateTime.now().minusMinutes(1));
        when(setOps.members("presence:user:sessions:user@example.com")).thenReturn(Set.of());
        when(userRepo.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        assertTrue(presenceService.isOnline("user@example.com"));
    }
}
