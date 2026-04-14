package com.socialsea.controller;

import com.socialsea.model.LoginSession;
import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import com.socialsea.security.JwtUtil;
import com.socialsea.service.LoginSessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/security/sessions")
@RequiredArgsConstructor
public class LoginSessionController {

    private final UserRepository userRepository;
    private final LoginSessionService loginSessionService;
    private final JwtUtil jwtUtil;

    public record LoginSessionInfo(
            String sessionId,
            String deviceIdHint,
            String deviceName,
            String userAgent,
            String ipAddress,
            LocalDateTime createdAt,
            LocalDateTime lastSeenAt,
            boolean active,
            boolean current,
            String revokedReason
    ) {}

    @GetMapping
    public ResponseEntity<?> list(Authentication auth, HttpServletRequest request) {
        User me = currentUser(auth);
        if (me == null || me.getId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        String currentSessionId = resolveCurrentSessionId(request);
        List<LoginSession> sessions = loginSessionService.listRecentSessions(me.getId());
        List<LoginSessionInfo> info = sessions.stream().map(session -> {
            String sid = safe(session.getSessionId());
            boolean isCurrent = !currentSessionId.isBlank() && currentSessionId.equals(sid);
            boolean active = session.getRevokedAt() == null;
            return new LoginSessionInfo(
                    sid,
                    hint(session.getDeviceId()),
                    safe(session.getDeviceName()),
                    safe(session.getUserAgent()),
                    safe(session.getIpAddress()),
                    session.getCreatedAt(),
                    session.getLastSeenAt(),
                    active,
                    isCurrent,
                    safe(session.getRevokedReason())
            );
        }).toList();

        return ResponseEntity.ok(Map.of(
                "maxDevices", loginSessionService.getMaxDeviceSessions(),
                "policy", String.valueOf(loginSessionService.getPolicy()),
                "sessions", info
        ));
    }

    @PostMapping("/revoke/{sessionId}")
    public ResponseEntity<?> revoke(@PathVariable String sessionId, Authentication auth) {
        User me = currentUser(auth);
        if (me == null || me.getId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        String sid = safe(sessionId);
        if (sid.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "sessionId required"));
        }
        boolean ok = loginSessionService.revokeSession(me.getId(), sid, "user-request");
        return ResponseEntity.ok(Map.of("ok", ok));
    }

    @PostMapping("/revoke-others")
    public ResponseEntity<?> revokeOthers(Authentication auth, HttpServletRequest request) {
        User me = currentUser(auth);
        if (me == null || me.getId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        String currentSessionId = resolveCurrentSessionId(request);
        if (currentSessionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "current session not found"));
        }
        int revoked = loginSessionService.revokeOtherSessions(me.getId(), currentSessionId, "user-request");
        return ResponseEntity.ok(Map.of("ok", true, "revoked", revoked));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutCurrent(Authentication auth, HttpServletRequest request) {
        User me = currentUser(auth);
        if (me == null || me.getId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        String currentSessionId = resolveCurrentSessionId(request);
        if (!currentSessionId.isBlank()) {
            loginSessionService.revokeSession(me.getId(), currentSessionId, "logout");
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private User currentUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        String email = safe(auth.getName());
        if (email.isBlank()) return null;
        return userRepository.findByEmailIgnoreCase(email).orElse(null);
    }

    private String resolveCurrentSessionId(HttpServletRequest request) {
        String token = extractBearerToken(request);
        if (token.isBlank()) return "";
        try {
            String sid = jwtUtil.extractTokenId(token);
            return safe(sid);
        } catch (Exception e) {
            return "";
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        if (request == null) return "";
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null) return "";
        String value = authHeader.trim();
        if (!value.startsWith("Bearer ")) return "";
        return value.substring(7).trim();
    }

    private String hint(String deviceId) {
        String raw = safe(deviceId);
        if (raw.isBlank()) return "";
        if (raw.length() <= 8) return raw;
        return raw.substring(0, 4) + "…" + raw.substring(raw.length() - 4);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
