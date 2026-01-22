package com.socialsea.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class EmailOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String otp;
    private LocalDateTime expiresAt;
    private boolean verified = false;
    private int failedAttempts = 0;

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getOtp() {
        return otp;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public boolean isVerified() {
        return verified;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }
}
