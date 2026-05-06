package com.socialsea.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/vault")
@CrossOrigin(origins = {"https://socialsea.netlify.app",
        "https://socialsea.co.in",
        "https://www.socialsea.co.in", "http://localhost:5173", "http://127.0.0.1:5173", "http://43.205.229.211:5173"})
@RequiredArgsConstructor
public class VaultLockController {

    private final UserRepository userRepo;
    private final ObjectMapper objectMapper;

    @GetMapping("/lock")
    public ResponseEntity<?> getLock(Authentication auth) {
        Optional<User> meOpt = resolveAuthenticatedUser(auth);
        if (meOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User me = meOpt.get();
        String raw = me.getVaultLockJson();
        if (raw == null || raw.isBlank()) {
            return ResponseEntity.ok(Map.of("configured", false));
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(raw, new TypeReference<>() {});
            List<String> imageIds = sanitizeImageIds(parsed.get("imageIds"));
            if (imageIds.isEmpty()) {
                return ResponseEntity.ok(Map.of("configured", false));
            }
            long createdAt = parseCreatedAt(parsed.get("createdAt"));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("configured", true);
            out.put("imageIds", imageIds);
            out.put("createdAt", createdAt > 0 ? createdAt : System.currentTimeMillis());
            out.put("updatedAt", me.getVaultLockUpdatedAt());
            return ResponseEntity.ok(out);
        } catch (Exception ignored) {
            return ResponseEntity.ok(Map.of("configured", false));
        }
    }

    @PostMapping("/lock")
    public ResponseEntity<?> saveLock(@RequestBody(required = false) Map<String, Object> body, Authentication auth) {
        Optional<User> meOpt = resolveAuthenticatedUser(auth);
        if (meOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        List<String> imageIds = sanitizeImageIds(body == null ? null : body.get("imageIds"));
        if (imageIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "imageIds is required"));
        }
        if (imageIds.size() > 20) {
            return ResponseEntity.badRequest().body(Map.of("message", "imageIds is too large"));
        }

        User me = meOpt.get();
        long createdAt = parseCreatedAt(body == null ? null : body.get("createdAt"));
        if (createdAt <= 0) {
            createdAt = System.currentTimeMillis();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("imageIds", imageIds);
        payload.put("createdAt", createdAt);

        try {
            me.setVaultLockJson(objectMapper.writeValueAsString(payload));
            me.setVaultLockUpdatedAt(LocalDateTime.now());
            userRepo.save(me);
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "configured", true,
                    "imageIds", imageIds,
                    "createdAt", createdAt
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unable to save vault lock"));
        }
    }

    @DeleteMapping("/lock")
    public ResponseEntity<?> clearLock(Authentication auth) {
        Optional<User> meOpt = resolveAuthenticatedUser(auth);
        if (meOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User me = meOpt.get();
        me.setVaultLockJson(null);
        me.setVaultLockUpdatedAt(LocalDateTime.now());
        userRepo.save(me);
        return ResponseEntity.ok(Map.of("ok", true, "configured", false));
    }

    private Optional<User> resolveAuthenticatedUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        String identifier = String.valueOf(auth.getName()).trim();
        if (identifier.isEmpty()) {
            return Optional.empty();
        }
        return userRepo.findByEmailIgnoreCase(identifier)
                .or(() -> userRepo.findByNameIgnoreCase(identifier));
    }

    private List<String> sanitizeImageIds(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object raw : list) {
            String id = String.valueOf(raw == null ? "" : raw).trim();
            if (id.isEmpty()) continue;
            out.add(id);
        }
        return out;
    }

    private long parseCreatedAt(Object value) {
        if (value == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
