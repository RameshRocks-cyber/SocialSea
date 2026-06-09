package com.socialsea.service;

import com.socialsea.model.LoginSession;
import com.socialsea.model.User;
import com.socialsea.repository.LoginSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class LoginSessionService {

    public enum DeviceSessionPolicy {
        EVICT_OLDEST,
        REJECT
    }

    private static final Duration TOUCH_THROTTLE = Duration.ofSeconds(45);

    private final LoginSessionRepository repo;
    private final int maxDeviceSessions;
    private final DeviceSessionPolicy policy;

    public LoginSessionService(
            LoginSessionRepository repo,
            @Value("${app.security.max-device-sessions:2}") int maxDeviceSessions,
            @Value("${app.security.device-session.policy:evict-oldest}") String policyRaw
    ) {
        this.repo = repo;
        this.maxDeviceSessions = Math.max(1, maxDeviceSessions);
        this.policy = parsePolicy(policyRaw);
    }

    public int getMaxDeviceSessions() {
        return maxDeviceSessions;
    }

    public DeviceSessionPolicy getPolicy() {
        return policy;
    }

    private DeviceSessionPolicy parsePolicy(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if ("reject".equals(value)) return DeviceSessionPolicy.REJECT;
        return DeviceSessionPolicy.EVICT_OLDEST;
    }

    public String resolveDeviceId(@NonNull HttpServletRequest request, String suggested) {
        String header = firstNonBlank(
                request.getHeader("X-Device-Id"),
                request.getHeader("X-DeviceId"),
                request.getHeader("X-Client-Device-Id")
        );
        String candidate = firstNonBlank(header, suggested);
        String normalized = normalizeDeviceId(candidate);
        if (!normalized.isBlank()) return normalized;
        return "srv_" + UUID.randomUUID();
    }

    public String resolveDeviceName(@NonNull HttpServletRequest request, String suggested) {
        String header = firstNonBlank(
                request.getHeader("X-Device-Name"),
                request.getHeader("X-DeviceName")
        );
        String candidate = firstNonBlank(suggested, header);
        String value = candidate == null ? "" : candidate.trim();
        if (value.isBlank()) return "";
        return value.length() > 200 ? value.substring(0, 200) : value;
    }

    public String resolveUserAgent(@NonNull HttpServletRequest request) {
        String raw = request.getHeader("User-Agent");
        String value = raw == null ? "" : raw.trim();
        if (value.length() > 512) value = value.substring(0, 512);
        return value;
    }

    public String resolveIp(@NonNull HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            if (parts.length > 0) {
                String first = parts[0].trim();
                if (!first.isBlank()) return truncate(first, 64);
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return truncate(realIp.trim(), 64);
        String addr = request.getRemoteAddr();
        return truncate(addr == null ? "" : addr.trim(), 64);
    }

    @Transactional
    public LoginSession startSession(User user, HttpServletRequest request, String suggestedDeviceId, String suggestedDeviceName) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("user required");
        }
        if (request == null) {
            throw new IllegalArgumentException("request required");
        }
        if (user.isBanned()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User banned");
        }

        String deviceId = resolveDeviceId(request, suggestedDeviceId);
        String deviceName = resolveDeviceName(request, suggestedDeviceName);
        String ua = resolveUserAgent(request);
        String ip = resolveIp(request);
        LocalDateTime now = LocalDateTime.now();

        // Keep 1 active session per device (rotate tokens on re-login).
        List<LoginSession> existingDeviceSessions = repo.findByUserIdAndDeviceIdAndRevokedAtIsNullOrderByCreatedAtDesc(
                user.getId(),
                deviceId
        );
        for (LoginSession existing : existingDeviceSessions) {
            revoke(existing, now, "superseded");
        }

        LoginSession session = new LoginSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(user.getId());
        session.setDeviceId(deviceId);
        session.setDeviceName(deviceName);
        session.setUserAgent(ua);
        session.setIpAddress(ip);
        session.setCreatedAt(now);
        session.setLastSeenAt(now);
        repo.save(session);

        enforceLimit(user.getId(), session.getSessionId(), now);
        return session;
    }

    @Transactional
    public void touch(String sessionId) {
        String sid = sessionId == null ? "" : sessionId.trim();
        if (sid.isBlank()) return;
        LoginSession session = repo.findBySessionId(sid).orElse(null);
        if (session == null || session.getRevokedAt() != null) return;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastSeen = session.getLastSeenAt();
        if (lastSeen != null && Duration.between(lastSeen, now).compareTo(TOUCH_THROTTLE) < 0) {
            return;
        }
        session.setLastSeenAt(now);
        repo.save(session);
    }

    @Transactional(readOnly = true)
    public boolean isActiveSession(Long userId, String sessionId) {
        if (userId == null) return false;
        String sid = sessionId == null ? "" : sessionId.trim();
        if (sid.isBlank()) return false;
        LoginSession session = repo.findByUserIdAndSessionId(userId, sid).orElse(null);
        return session != null && session.getRevokedAt() == null;
    }

    @Transactional(readOnly = true)
    public List<LoginSession> listRecentSessions(Long userId) {
        if (userId == null) return List.of();
        return repo.findTop25ByUserIdOrderByLastSeenAtDescCreatedAtDesc(userId);
    }

    @Transactional
    public boolean revokeSession(Long userId, String sessionId, String reason) {
        if (userId == null) return false;
        String sid = sessionId == null ? "" : sessionId.trim();
        if (sid.isBlank()) return false;
        LoginSession session = repo.findByUserIdAndSessionId(userId, sid).orElse(null);
        if (session == null || session.getRevokedAt() != null) return false;
        LocalDateTime now = LocalDateTime.now();
        revoke(session, now, normalizeReason(reason));
        repo.save(session);
        return true;
    }

    @Transactional
    public int revokeOtherSessions(Long userId, String keepSessionId, String reason) {
        if (userId == null) return 0;
        String keep = keepSessionId == null ? "" : keepSessionId.trim();
        LocalDateTime now = LocalDateTime.now();
        int revoked = 0;
        List<LoginSession> active = repo.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDescCreatedAtDesc(userId);
        for (LoginSession session : active) {
            if (keep.equals(session.getSessionId())) continue;
            revoke(session, now, normalizeReason(reason));
            repo.save(session);
            revoked++;
        }
        return revoked;
    }

    private void enforceLimit(Long userId, String currentSessionId, LocalDateTime now) {
        List<LoginSession> active = repo.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDescCreatedAtDesc(userId);
        if (active.size() <= maxDeviceSessions) return;

        // Ensure deterministic ordering (oldest last).
        List<LoginSession> sorted = new ArrayList<>(active);
        sorted.sort(Comparator
                .comparing((LoginSession s) -> s.getLastSeenAt() == null ? LocalDateTime.MIN : s.getLastSeenAt())
                .thenComparing(s -> s.getCreatedAt() == null ? LocalDateTime.MIN : s.getCreatedAt())
        );

        int overflow = sorted.size() - maxDeviceSessions;
        List<LoginSession> toRevoke = new ArrayList<>();
        for (LoginSession session : sorted) {
            if (overflow <= 0) break;
            if (session.getSessionId() != null && session.getSessionId().equals(currentSessionId)) {
                continue;
            }
            toRevoke.add(session);
            overflow--;
        }

        if (toRevoke.isEmpty()) return;

        if (policy == DeviceSessionPolicy.REJECT) {
            // Undo the current session and keep existing ones.
            LoginSession current = repo.findBySessionId(currentSessionId).orElse(null);
            if (current != null && current.getRevokedAt() == null) {
                revoke(current, now, "device-limit");
                repo.save(current);
            }
            throw new DeviceSessionLimitException("Only " + maxDeviceSessions + " devices can be logged in at the same time.");
        }

        for (LoginSession session : toRevoke) {
            revoke(session, now, "device-limit");
            repo.save(session);
        }
    }

    private void revoke(LoginSession session, LocalDateTime now, String reason) {
        if (session == null) return;
        if (session.getRevokedAt() != null) return;
        session.setRevokedAt(now);
        session.setRevokedReason(normalizeReason(reason));
    }

    private String normalizeDeviceId(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) return "";
        if (value.length() > 128) value = value.substring(0, 128);
        // allow typical UUID / random tokens
        value = value.replaceAll("[^a-zA-Z0-9._:-]", "");
        return value.trim();
    }

    private String normalizeReason(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) return "";
        if (value.length() > 64) value = value.substring(0, 64);
        return value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value == null) continue;
            String trimmed = value.trim();
            if (!trimmed.isBlank()) return trimmed;
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.length() <= max) return trimmed;
        return trimmed.substring(0, max);
    }
}
