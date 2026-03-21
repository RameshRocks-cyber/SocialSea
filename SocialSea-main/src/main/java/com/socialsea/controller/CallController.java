package com.socialsea.controller;

import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/calls")
public class CallController {

    private final UserRepository userRepo;
    private final ConcurrentHashMap<String, List<Map<String, Object>>> inboxByUser = new ConcurrentHashMap<>();

    public CallController(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @GetMapping("/inbox")
    public ResponseEntity<?> inbox(Authentication auth) {
        User me = requireUser(auth);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        List<Map<String, Object>> list = inboxByUser.getOrDefault(String.valueOf(me.getId()), List.of());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/signal/{targetId}")
    public ResponseEntity<?> signal(
        @PathVariable("targetId") String targetId,
        @RequestBody(required = false) Map<String, Object> body,
        Authentication auth
    ) {
        Map<String, Object> payload = body != null ? new java.util.HashMap<>(body) : new java.util.HashMap<>();
        User sender = requireUser(auth);
        if (sender != null) {
            payload.putIfAbsent("fromUserId", sender.getId());
            payload.putIfAbsent("fromEmail", sender.getEmail());
        }

        Long toUserId = parseLong(targetId);
        if (toUserId != null) {
            payload.putIfAbsent("toUserId", toUserId);
        }
        payload.putIfAbsent("timestamp", System.currentTimeMillis());

        String key = String.valueOf(targetId);
        List<Map<String, Object>> inbox = inboxByUser.computeIfAbsent(
            key,
            k -> Collections.synchronizedList(new ArrayList<>())
        );
        inbox.add(payload);
        if (inbox.size() > 200) {
            inbox.subList(0, inbox.size() - 200).clear();
        }

        return ResponseEntity.ok(Map.of("ok", true));
    }

    private User requireUser(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) return null;
        return userRepo.findByEmail(auth.getName()).orElse(null);
    }

    private Long parseLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
