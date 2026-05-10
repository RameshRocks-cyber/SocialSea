package com.socialsea.controller;

import com.socialsea.service.WebPushService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications/push")
public class NotificationPushController {

    private final WebPushService webPushService;

    public NotificationPushController(WebPushService webPushService) {
        this.webPushService = webPushService;
    }

    @GetMapping("/config")
    public ResponseEntity<?> config(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        return ResponseEntity.ok(Map.of(
                "configured", webPushService.isConfigured(),
                "publicKey", webPushService.getPublicKey()
        ));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(
            @RequestBody(required = false) Map<String, Object> body,
            Authentication auth,
            HttpServletRequest request
    ) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        String endpoint = asString(body != null ? body.get("endpoint") : null);
        Map<String, Object> keys = asMap(body != null ? body.get("keys") : null);
        String p256dh = asString(keys.get("p256dh"));
        String authKey = asString(keys.get("auth"));
        Long expirationTime = toLong(body != null ? body.get("expirationTime") : null);
        if (endpoint.isBlank() || p256dh.isBlank() || authKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid push subscription payload"));
        }

        webPushService.upsertSubscription(
                auth.getName(),
                endpoint,
                p256dh,
                authKey,
                request != null ? request.getHeader("User-Agent") : null,
                expirationTime
        );

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "configured", webPushService.isConfigured()
        ));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribe(
            @RequestBody(required = false) Map<String, Object> body,
            Authentication auth
    ) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        String endpoint = asString(body != null ? body.get("endpoint") : null);
        if (endpoint.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "endpoint is required"));
        }

        webPushService.deactivateSubscription(auth.getName(), endpoint);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Map<String, Object> asMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) return Map.of();
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry == null || entry.getKey() == null) continue;
            out.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return out;
    }

    private Long toLong(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (Exception ignored) {
            return null;
        }
    }
}
