package com.socialsea.service;

import com.socialsea.dto.OtpSendResult;
import com.socialsea.model.EmailOtp;
import com.socialsea.repository.EmailOtpRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
public class OtpService {
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    @Autowired
    private EmailOtpRepository otpRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private Environment environment;

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

        EmailOtp emailOtp = otpRepository
                .findTopByEmailAndVerifiedFalseOrderByExpiresAtDesc(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP expired or not found"));

        if (emailOtp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP expired");
        }

        if (emailOtp.getAttempts() >= 5) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. OTP blocked.");
        }

        if (!emailOtp.getOtp().equals(otp)) {
            emailOtp.setAttempts(emailOtp.getAttempts() + 1);
            otpRepository.save(emailOtp);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OTP");
        }

        otpRepository.delete(emailOtp);
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

    private String createOrUpdateOtpRecord(String identifier) {
        log.info("OTP service hit for {}", identifier);

        List<EmailOtp> otps = otpRepository.findByEmailOrderByExpiresAtDesc(identifier);
        EmailOtp otp = otps.isEmpty() ? null : otps.get(0);
        LocalDateTime now = LocalDateTime.now();
        String code = String.valueOf(100000 + new Random().nextInt(900000));

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
}
