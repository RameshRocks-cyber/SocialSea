package com.socialsea.service;

import com.socialsea.dto.OtpSendResult;
import com.socialsea.model.EmailOtp;
import com.socialsea.repository.EmailOtpRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceRedisTest {

    @Mock
    private EmailOtpRepository otpRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private Environment environment;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations hashOperations;

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService();
        otpService.otpRepository = otpRepository;
        otpService.emailService = emailService;
        otpService.environment = environment;
        otpService.redisTemplate = redisTemplate;
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void sendOtpStoresRecordInRedis() {
        when(hashOperations.entries("otp:user@example.com")).thenReturn(Collections.emptyMap());

        OtpSendResult result = otpService.sendOtp("user@example.com");

        assertNotNull(result.getOtp());
        ArgumentCaptor<Map<String, String>> valuesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(anyString(), valuesCaptor.capture());
        Map<String, String> saved = valuesCaptor.getValue();
        assertEquals("user@example.com", saved.get("email"));
        assertEquals(result.getOtp(), saved.get("otp"));
        assertEquals("0", saved.get("attempts"));
        assertEquals("1", saved.get("resendCount"));
        verify(emailService).sendOtpEmail("user@example.com", result.getOtp());
    }

    @Test
    void verifyOtpDeletesRedisRecordOnSuccess() {
        when(hashOperations.entries("otp:user@example.com")).thenReturn(Map.of(
                "email", "user@example.com",
                "otp", "123456",
                "attempts", "0",
                "resendCount", "1",
                "lastSentAt", Instant.now().minusSeconds(30).toString(),
                "expiresAt", Instant.now().plusSeconds(240).toString(),
                "verified", "false"
        ));

        otpService.verifyOtp("user@example.com", "123456");

        verify(redisTemplate).delete("otp:user@example.com");
        verify(otpRepository, never()).findTopByEmailAndVerifiedFalseOrderByExpiresAtDesc(anyString());
    }

    @Test
    void sendOtpFallsBackToDatabaseWhenRedisFails() {
        when(hashOperations.entries("otp:user@example.com")).thenThrow(new RuntimeException("redis offline"));
        when(otpRepository.findByEmailOrderByExpiresAtDesc("user@example.com")).thenReturn(List.of());

        OtpSendResult result = otpService.sendOtp("user@example.com");

        assertNotNull(result.getOtp());
        ArgumentCaptor<EmailOtp> otpCaptor = ArgumentCaptor.forClass(EmailOtp.class);
        verify(otpRepository).save(otpCaptor.capture());
        EmailOtp saved = otpCaptor.getValue();
        assertEquals("user@example.com", saved.getEmail());
        assertEquals(result.getOtp(), saved.getOtp());
        verify(emailService).sendOtpEmail("user@example.com", result.getOtp());
    }
}
