package com.socialsea.service;

import com.socialsea.dto.OtpSendResult;
import com.socialsea.model.EmailOtp;
import com.socialsea.repository.EmailOtpRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OtpService {
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 5;
    private static final String REDIS_KEY_PREFIX = "otp:";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_OTP = "otp";
    private static final String FIELD_ATTEMPTS = "attempts";
    private static final String FIELD_RESEND_COUNT = "resendCount";
    private static final String FIELD_LAST_SENT_AT = "lastSentAt";
    private static final String FIELD_EXPIRES_AT = "expiresAt";
    private static final String FIELD_VERIFIED = "verified";

    @Autowired
    EmailOtpRepository otpRepository;

    @Autowired
    EmailService emailService;

    @Autowired
    Environment environment;

    @Autowired(required = false)
    StringRedisTemplate redisTemplate;

    @Transactional
    public OtpSendResult sendOtp(String email) {
        String code = createOrUpdateOtpRecord(email);
        if (isDevProfile()) {
            log.info("DEV OTP for {} is {}", email, code);
        }
        try {
            emailService.sendOtpEmail(email, code);
            return new OtpSendResult(code, false, null);
        } catch (RuntimeException ex) {
            log.warn("OTP email send skipped/failure for {}: {}", email, ex.getMessage());
            // Keep API responses controlled: surface delivery failure to the caller
            // instead of bubbling runtime exceptions as HTTP 500.
            log.info("Fallback OTP for {} is {}", email, code);
            return new OtpSendResult(code, true, ex.getMessage());
        }
    }

    @Transactional
    public OtpSendResult sendOtpToPhone(String phoneNumber) {
        String code = createOrUpdateOtpRecord(phoneNumber);
        log.info("OTP generated for phone {}", phoneNumber);
        // SMS provider is not configured yet. Keep flow unblocked with fallback OTP.
        return new OtpSendResult(code, true, "SMS gateway not configured");
    }

    @Transactional
    public void verifyOtp(String email, String otp) {
        String identifier = normalizeIdentifier(email);
        if (identifier == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP expired or not found");
        }

        try {
            if (verifyOtpRedis(identifier, otp)) {
                return;
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.debug("Redis OTP verification unavailable for {}: {}", identifier, ex.getMessage());
        }

        verifyOtpDb(identifier, otp);
    }

    private boolean verifyOtpRedis(String identifier, String otp) {
        OtpRecord record = readRedisRecord(identifier).orElse(null);
        if (record == null) {
            return false;
        }

        Instant now = Instant.now();
        if (record.expiresAt != null && record.expiresAt.isBefore(now)) {
            deleteRedisRecord(identifier);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP expired");
        }

        if (record.attempts >= MAX_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. OTP blocked.");
        }

        if (!safeEquals(record.otp, otp)) {
            record.attempts += 1;
            saveRedisRecord(identifier, record, remainingTtl(record, now));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OTP");
        }

        try {
            deleteRedisRecord(identifier);
        } catch (RuntimeException ex) {
            log.debug("Unable to delete Redis OTP for {} after success: {}", identifier, ex.getMessage());
        }
        return true;
    }

    private String createOrUpdateOtpRecord(String identifier) {
        String normalized = normalizeIdentifier(identifier);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email or mobile number required");
        }

        try {
            if (redisTemplate != null) {
                return createOrUpdateOtpRecordRedis(normalized);
            }
        } catch (RuntimeException ex) {
            log.debug("Redis OTP unavailable for {}: {}", normalized, ex.getMessage());
        }

        return createOrUpdateOtpRecordDb(normalized);
    }

    private String createOrUpdateOtpRecordRedis(String identifier) {
        Instant now = Instant.now();
        OtpRecord existing = readRedisRecord(identifier).orElse(null);
        OtpRecord record = existing != null && !isExpired(existing, now) ? existing : new OtpRecord(identifier);
        String code = generateOtp();

        record.otp = code;
        record.resendCount = existing == null || isExpired(existing, now) ? 1 : existing.resendCount + 1;
        record.attempts = 0;
        record.lastSentAt = now;
        record.expiresAt = now.plus(OTP_TTL);
        record.verified = false;

        saveRedisRecord(identifier, record, OTP_TTL);
        return code;
    }

    private String createOrUpdateOtpRecordDb(String identifier) {
        log.info("OTP service hit for {}", identifier);

        List<EmailOtp> otps = otpRepository.findByEmailOrderByExpiresAtDesc(identifier);
        EmailOtp otp = otps.isEmpty() ? null : otps.get(0);
        LocalDateTime now = LocalDateTime.now();
        String code = generateOtp();

        if (otp == null) {
            otp = new EmailOtp();
            otp.setEmail(identifier);
            otp.setOtp(code);
            otp.setResendCount(1);
            otp.setAttempts(0);
        } else {
            otp.setOtp(code);
            otp.setResendCount(otp.getResendCount() + 1);
        }

        otp.setAttempts(0);
        otp.setLastSentAt(now);
        otp.setExpiresAt(now.plusMinutes(5));
        otp.setVerified(false);
        otpRepository.save(otp);

        return code;
    }

    private void verifyOtpDb(String email, String otp) {
        EmailOtp emailOtp = otpRepository
                .findTopByEmailAndVerifiedFalseOrderByExpiresAtDesc(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP expired or not found"));

        if (emailOtp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP expired");
        }

        if (emailOtp.getAttempts() >= MAX_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. OTP blocked.");
        }

        if (!safeEquals(emailOtp.getOtp(), otp)) {
            emailOtp.setAttempts(emailOtp.getAttempts() + 1);
            otpRepository.save(emailOtp);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OTP");
        }

        otpRepository.delete(emailOtp);
    }

    private Optional<OtpRecord> readRedisRecord(String identifier) {
        if (redisTemplate == null) {
            return Optional.empty();
        }

        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        Map<String, String> values = hashOps.entries(redisKey(identifier));
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(OtpRecord.fromMap(identifier, values));
    }

    private void saveRedisRecord(String identifier, OtpRecord record, Duration ttl) {
        if (redisTemplate == null || record == null) {
            return;
        }

        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        hashOps.putAll(redisKey(identifier), record.toMap());
        Duration safeTtl = ttl == null || ttl.isNegative() || ttl.isZero() ? OTP_TTL : ttl;
        redisTemplate.expire(redisKey(identifier), safeTtl);
    }

    private void deleteRedisRecord(String identifier) {
        if (redisTemplate == null) {
            return;
        }

        redisTemplate.delete(redisKey(identifier));
    }

    private Duration remainingTtl(OtpRecord record, Instant now) {
        if (record == null || record.expiresAt == null || now == null) {
            return OTP_TTL;
        }
        Duration remaining = Duration.between(now, record.expiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            return Duration.ofSeconds(1);
        }
        return remaining;
    }

    private boolean isExpired(OtpRecord record, Instant now) {
        return record == null || record.expiresAt == null || now == null || !record.expiresAt.isAfter(now);
    }

    private String redisKey(String identifier) {
        return REDIS_KEY_PREFIX + identifier;
    }

    private String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        String trimmed = identifier.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private String generateOtp() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
    }

    private boolean safeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().equals(right.trim());
    }

    private boolean isDevProfile() {
        try {
            for (String profile : environment.getActiveProfiles()) {
                if ("dev".equalsIgnoreCase(profile)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return false;
    }

    private static final class OtpRecord {
        private final String email;
        private String otp;
        private int attempts;
        private int resendCount;
        private Instant lastSentAt;
        private Instant expiresAt;
        private boolean verified;

        private OtpRecord(String email) {
            this.email = email;
        }

        private static OtpRecord fromMap(String identifier, Map<String, String> values) {
            OtpRecord record = new OtpRecord(identifier);
            record.otp = values.get(FIELD_OTP);
            record.attempts = parseInt(values.get(FIELD_ATTEMPTS), 0);
            record.resendCount = parseInt(values.get(FIELD_RESEND_COUNT), 0);
            record.lastSentAt = parseInstant(values.get(FIELD_LAST_SENT_AT));
            record.expiresAt = parseInstant(values.get(FIELD_EXPIRES_AT));
            record.verified = Boolean.parseBoolean(String.valueOf(values.get(FIELD_VERIFIED)));
            return record;
        }

        private Map<String, String> toMap() {
            Map<String, String> values = new HashMap<>();
            values.put(FIELD_EMAIL, email);
            values.put(FIELD_OTP, otp);
            values.put(FIELD_ATTEMPTS, String.valueOf(attempts));
            values.put(FIELD_RESEND_COUNT, String.valueOf(resendCount));
            values.put(FIELD_LAST_SENT_AT, lastSentAt != null ? lastSentAt.toString() : null);
            values.put(FIELD_EXPIRES_AT, expiresAt != null ? expiresAt.toString() : null);
            values.put(FIELD_VERIFIED, String.valueOf(verified));
            values.entrySet().removeIf(entry -> entry.getValue() == null);
            return values;
        }

        private static int parseInt(String value, int fallback) {
            try {
                return value == null ? fallback : Integer.parseInt(value.trim());
            } catch (Exception ex) {
                return fallback;
            }
        }

        private static Instant parseInstant(String value) {
            try {
                return value == null || value.isBlank() ? null : Instant.parse(value.trim());
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
