package com.socialsea.service;

import com.socialsea.model.EmailOtp;
import com.socialsea.repository.EmailOtpRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

@Service
public class OtpService {

    @Autowired
    private EmailOtpRepository otpRepository;

    public String generateOtp(String email) {

        LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(10);

        int recentOtpCount = otpRepository
                .findByEmailAndExpiresAtAfter(email, tenMinutesAgo)
                .size();

        if (recentOtpCount >= 3) {
            throw new RuntimeException("Too many OTP requests. Please try again after 10 minutes.");
        }

        String otp = String.valueOf(100000 + new Random().nextInt(900000));

        EmailOtp emailOtp = new EmailOtp();
        emailOtp.setEmail(email);
        emailOtp.setOtp(otp);
        emailOtp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        emailOtp.setVerified(false);
        emailOtp.setFailedAttempts(0);

        otpRepository.save(emailOtp);
        return otp;
    }

    public void verifyOtp(String email, String otp) {

        EmailOtp emailOtp = otpRepository
                .findTopByEmailOrderByIdDesc(email)
                .orElseThrow(() -> new RuntimeException("OTP not found"));

        if (emailOtp.isVerified()) {
            throw new RuntimeException("OTP already used");
        }

        if (emailOtp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP expired");
        }

        if (emailOtp.getFailedAttempts() >= 5) {
            throw new RuntimeException("Too many failed attempts. OTP blocked.");
        }

        if (!emailOtp.getOtp().equals(otp)) {
            emailOtp.setFailedAttempts(emailOtp.getFailedAttempts() + 1);
            otpRepository.save(emailOtp);
            throw new RuntimeException("Invalid OTP");
        }

        emailOtp.setVerified(true);
        otpRepository.save(emailOtp);
    }
}
