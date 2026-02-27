package com.socialsea.repository;

import com.socialsea.model.LoginLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;

public interface LoginLogRepository extends JpaRepository<LoginLog, Long> {
    long countByIpAddressAndSuccessFalseAndCreatedAtAfter(String ipAddress, LocalDateTime createdAt);
}