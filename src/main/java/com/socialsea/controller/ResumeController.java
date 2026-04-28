package com.socialsea.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final UserRepository userRepo;
    private final ObjectMapper objectMapper;
    private final UploadService uploadService;

    @GetMapping("/me")
    public ResponseEntity<?> getMyResume(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        User user = userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String payload = user.getResumeJson();
        if (payload == null || payload.isBlank()) {
            return ResponseEntity.ok(new HashMap<>());
        }

        try {
            Object data = objectMapper.readValue(payload, Object.class);
            return ResponseEntity.ok(data);
        } catch (Exception ex) {
            return ResponseEntity.ok(new HashMap<>());
        }
    }

    @PutMapping("/me")
    public ResponseEntity<?> saveMyResume(@RequestBody(required = false) Object body, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        User user = userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Object safeBody = body == null ? new HashMap<>() : body;

        try {
            String payload = objectMapper.writeValueAsString(safeBody);
            user.setResumeJson(payload);
            user.setResumeUpdatedAt(LocalDateTime.now());
            userRepo.save(user);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid resume payload"));
        }

        return ResponseEntity.ok(safeBody);
    }

    @PostMapping("/media")
    public ResponseEntity<?> uploadResumeMedia(
            @RequestParam("file") MultipartFile file,
            Authentication auth
    ) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File is required"));
        }

        try {
            String url = uploadService.upload(file);
            String contentType = file.getContentType();
            String mediaType = contentType != null && contentType.startsWith("video") ? "VIDEO" : "IMAGE";
            return ResponseEntity.ok(Map.of(
                    "mediaUrl", url,
                    "mediaType", mediaType
            ));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", ex.getMessage()));
        }
    }
}
