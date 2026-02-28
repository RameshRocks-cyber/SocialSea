package com.socialsea.service;

import com.socialsea.model.EmailOtp;
import com.socialsea.repository.EmailOtpRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
public class OtpService {

    @Autowired
    private EmailOtpRepository otpRepository;

    @Autowired
    private EmailService emailService;

    @Transactional
    public void sendOtp(String email) {

        System.out.println("OTP SERVICE HIT - VERSION 2026-02-06-B");

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
        emailService.sendOtpEmail(email, code);
    }

    @Transactional
    public void verifyOtp(String email, String otp) {

        EmailOtp emailOtp = otpRepository
                .findTopByEmailAndVerifiedFalseOrderByExpiresAtDesc(email)
                .orElseThrow(() -> new RuntimeException("OTP expired or not found"));

        if (emailOtp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP expired");
        }

        if (emailOtp.getAttempts() >= 5) {
            throw new RuntimeException("Too many failed attempts. OTP blocked.");
        }

        if (!emailOtp.getOtp().equals(otp)) {
            emailOtp.setAttempts(emailOtp.getAttempts() + 1);
            otpRepository.save(emailOtp);
            throw new RuntimeException("Invalid OTP");
        }

        otpRepository.delete(emailOtp);
    }
}
