package com.socialsea.repository;

import com.socialsea.model.EmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {

    Optional<EmailOtp> findTopByEmailOrderByIdDesc(String email);

    List<EmailOtp> findByEmailAndExpiresAtAfter(String email, LocalDateTime time);
}
