package com.socialsea.controller;

import com.socialsea.dto.LiveBroadcastDto;
import com.socialsea.service.LiveBroadcastService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/live-broadcast")
@CrossOrigin(origins = {
        "https://socialsea.netlify.app",
        "https://socialsea.co.in",
        "https://www.socialsea.co.in",
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://43.205.229.211:5173"
})
public class LiveBroadcastController {

    private final LiveBroadcastService liveBroadcastService;

    public LiveBroadcastController(LiveBroadcastService liveBroadcastService) {
        this.liveBroadcastService = liveBroadcastService;
    }

    @GetMapping("/active")
    public ResponseEntity<?> active() {
        return ResponseEntity.ok(liveBroadcastService.getActive());
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody(required = false) LiveBroadcastDto payload, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        LiveBroadcastDto next = liveBroadcastService.start(payload, auth.getName());
        return ResponseEntity.ok(next);
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stop(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        liveBroadcastService.stop(auth.getName());
        return ResponseEntity.ok(Map.of("active", false));
    }
}
