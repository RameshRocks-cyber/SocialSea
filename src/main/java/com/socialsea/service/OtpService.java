package com.socialsea.service;

import com.socialsea.model.EmailOtp;
import com.socialsea.repository.EmailOtpRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.otp.allow-email-failure:false}")
    private boolean allowEmailFailure;

    @Transactional
    public String sendOtp(String email) {

        log.info("OTP service hit for {}", email);

        List<EmailOtp> otps = otpRepository.findByEmailOrderByExpiresAtDesc(email);
        EmailOtp otp = otps.isEmpty() ? null : otps.get(0);
        LocalDateTime now = LocalDateTime.now();

        // DEV: bypass cooldown and resend limit for testing

        String code = String.valueOf(100000 + new Random().nextInt(900000));

        if (otp == null) {
            otp = new EmailOtp();
            otp.setEmail(email);
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
        if (isDevProfile()) {
            log.info("DEV OTP for {} is {}", email, code);
        }
        try {
            emailService.sendOtpEmail(email, code);
        } catch (RuntimeException ex) {
            log.warn("OTP email send skipped/failure for {}: {}", email, ex.getMessage());
            // Do not block login when email provider is temporarily down.
            // `allowEmailFailure` is kept for compatibility but sendOtp now always continues.
            if (allowEmailFailure || !isProdProfile()) {
                log.info("Fallback OTP for {} is {}", email, code);
            } else {
                throw ex;
            }
        }

        return code;
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

    private boolean isProdProfile() {
        try {
            for (String profile : environment.getActiveProfiles()) {
                if ("prod".equalsIgnoreCase(profile)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return false;
    }
}
