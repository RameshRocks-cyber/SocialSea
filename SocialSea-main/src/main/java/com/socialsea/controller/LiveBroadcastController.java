package com.socialsea.controller;

import com.socialsea.model.LiveBroadcast;
import com.socialsea.model.User;
import com.socialsea.repository.LiveBroadcastRepository;
import com.socialsea.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/live-broadcast")
public class LiveBroadcastController {

    private final LiveBroadcastRepository repo;
    private final UserRepository userRepo;

    public LiveBroadcastController(LiveBroadcastRepository repo, UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    @GetMapping("/active")
    public ResponseEntity<?> active() {
        LiveBroadcast active = repo.findFirstByActiveTrueOrderByStartedAtDesc().orElse(null);
        if (active == null) {
            return ResponseEntity.ok(Map.of("active", false));
        }

        long now = System.currentTimeMillis();
        Long expiresAt = active.getExpiresAt();
        if (expiresAt != null && expiresAt > 0 && expiresAt < now) {
            active.setActive(false);
            repo.save(active);
            return ResponseEntity.ok(Map.of("active", false));
        }

        return ResponseEntity.ok(toPayload(active));
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody(required = false) Map<String, Object> body, Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User user = userRepo.findByEmail(auth.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "User not found"));
        }

        List<LiveBroadcast> actives = repo.findByActiveTrueOrderByStartedAtDesc();
        for (LiveBroadcast item : actives) {
            item.setActive(false);
            repo.save(item);
        }

        Map<String, Object> safeBody = body != null ? body : Map.of();
        long now = System.currentTimeMillis();
        long startedAt = parseLong(safeBody.get("startedAt"), now);
        long expiresAt = parseLong(safeBody.get("expiresAt"), startedAt + 2 * 60 * 60 * 1000L);

        LiveBroadcast live = new LiveBroadcast();
        live.setActive(true);
        live.setTitle(stringOrNull(safeBody.get("title")));
        live.setHostName(stringOrNull(safeBody.get("hostName")));
        live.setHostEmail(user.getEmail());
        live.setHostUser(user);
        live.setStartedAt(startedAt);
        live.setExpiresAt(expiresAt);

        LiveBroadcast saved = repo.save(live);
        return ResponseEntity.ok(toPayload(saved));
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stop(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        List<LiveBroadcast> actives = repo.findByActiveTrueOrderByStartedAtDesc();
        for (LiveBroadcast item : actives) {
            item.setActive(false);
            repo.save(item);
        }
        return ResponseEntity.ok(Map.of("active", false));
    }

    private Map<String, Object> toPayload(LiveBroadcast live) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", live.getId());
        payload.put("title", live.getTitle());
        payload.put("hostName", live.getHostName());
        payload.put("hostEmail", live.getHostEmail());
        payload.put("hostUserId", live.getHostUser() != null ? live.getHostUser().getId() : null);
        payload.put("startedAt", live.getStartedAt());
        payload.put("expiresAt", live.getExpiresAt());
        payload.put("active", live.isActive());
        return payload;
    }

    private String stringOrNull(Object value) {
        String raw = value == null ? "" : String.valueOf(value).trim();
        return raw.isEmpty() ? null : raw;
    }

    private long parseLong(Object value, long fallback) {
        if (value == null) return fallback;
        try {
            long parsed = Long.parseLong(String.valueOf(value));
            return parsed > 0 ? parsed : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}
