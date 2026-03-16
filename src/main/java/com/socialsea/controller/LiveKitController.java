package com.socialsea.controller;

import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/livekit")
@CrossOrigin(origins = {
        "https://socialsea.netlify.app",
        "https://socialsea.co.in",
        "https://www.socialsea.co.in",
        "http://localhost:5173",
        "http://43.205.213.14:5173"
})
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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "LiveKit is not configured"));
        }
        String room = String.valueOf(payload.getOrDefault("room", "")).trim();
        if (room.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "room required"));
        }
        String identity = auth.getName();
        AccessToken token = new AccessToken(apiKey, apiSecret);
        token.setIdentity(identity);
        token.addGrants(new RoomJoin(true), new RoomName(room));
        return ResponseEntity.ok(Map.of("token", token.toJwt()));
    }
}
