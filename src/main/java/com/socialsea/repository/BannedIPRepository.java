package com.socialsea.repository;

import com.socialsea.model.BannedIP;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BannedIPRepository extends JpaRepository<BannedIP, Long> {
    Optional<BannedIP> findByIpAddress(String ipAddress);
}