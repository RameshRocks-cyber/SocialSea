package com.socialsea.repository;

import com.socialsea.model.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {

    Optional<LoginSession> findBySessionId(String sessionId);

    Optional<LoginSession> findByUserIdAndSessionId(Long userId, String sessionId);

    List<LoginSession> findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDescCreatedAtDesc(Long userId);

    List<LoginSession> findByUserIdAndDeviceIdAndRevokedAtIsNullOrderByCreatedAtDesc(Long userId, String deviceId);

    List<LoginSession> findTop25ByUserIdOrderByLastSeenAtDescCreatedAtDesc(Long userId);
}

