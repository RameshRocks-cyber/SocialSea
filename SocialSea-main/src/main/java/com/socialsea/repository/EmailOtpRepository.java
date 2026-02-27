package com.socialsea.repository;

import com.socialsea.model.EmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {

    // Used when checking resend limits
    List<EmailOtp> findByEmailOrderByExpiresAtDesc(String email);

    // Used during OTP verification
    Optional<EmailOtp> findTopByEmailAndVerifiedFalseOrderByExpiresAtDesc(String email);

    // Optional but useful
    Optional<EmailOtp> findTopByEmailOrderByIdDesc(String email);
}
