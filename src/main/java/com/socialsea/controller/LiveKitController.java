package com.socialsea.controller;

import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/livekit")
@CrossOrigin(origins = "${app.security.allowed-origins}")
@RequiredArgsConstructor
public class LiveKitController {

    @Value("${livekit.api-key:}")
    private String apiKey;

    @Value("${livekit.api-secret:}")
    private String apiSecret;

    @PostMapping("/token")
    public ResponseEntity<?> token(@RequestBody Map<String, Object> payload, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "message", "LiveKit is not configured on the backend. Set LIVEKIT_API_KEY and LIVEKIT_API_SECRET."
            ));
        }
        String room = String.valueOf(payload.getOrDefault("room", "")).trim();
        if (room.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "room required"));
        }
        String mode = String.valueOf(payload.getOrDefault("mode", "")).trim().toLowerCase();
        boolean viewer = Objects.equals(mode, "viewer");
        String identity = resolveIdentity(payload, auth);
        AccessToken token = new AccessToken(apiKey, apiSecret);
        token.setName(auth.getName());
        token.setIdentity(identity);
        token.addGrants(
                new RoomJoin(true),
                new RoomName(room),
                new CanSubscribe(true),
                new CanPublish(!viewer)
        );
        return ResponseEntity.ok(Map.of("token", token.toJwt()));
    }

    private String resolveIdentity(Map<String, Object> payload, Authentication auth) {
        String requested = String.valueOf(payload.getOrDefault("identity", "")).trim();
        String fallback = auth != null ? String.valueOf(auth.getName()).trim() : "";
        String base = sanitizeIdentity(!requested.isBlank() ? requested : fallback);
        if (!base.isBlank()) {
            return base;
        }
        return "user-" + System.currentTimeMillis();
    }

    private String sanitizeIdentity(String value) {
        if (value == null) return "";
        String sanitized = value.trim().replaceAll("[^a-zA-Z0-9_-]", "-");
        sanitized = sanitized.replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        if (sanitized.length() > 64) {
            sanitized = sanitized.substring(0, 64).replaceAll("-+$", "");
        }
        return sanitized;
    }
}
