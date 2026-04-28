package com.socialsea.controller;

import com.socialsea.model.AnonymousPost;
import com.socialsea.repository.AnonymousPostRepository;
import com.socialsea.service.UploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/anonymous")
public class AnonymousPostController {

    private final AnonymousPostRepository repo;
    private final UploadService uploadService;

    public AnonymousPostController(
        AnonymousPostRepository repo,
        UploadService uploadService
    ) {
        this.repo = repo;
        this.uploadService = uploadService;
    }

    @GetMapping("/feed")
    public ResponseEntity<?> feed() {
        List<AnonymousPost> items = repo.findByApprovedTrueAndRejectedFalseOrderByCreatedAtDesc();
        return ResponseEntity.ok(items);
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<?> like(@PathVariable Long id) {
        Long safeId = Objects.requireNonNull(id, "id");
        AnonymousPost post = repo.findById(safeId).orElse(null);
        if (post == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Anonymous post not found"));
        }
        post.setLikeCount(Math.max(0, post.getLikeCount() + 1));
        AnonymousPost saved = repo.save(post);
        return ResponseEntity.ok(Map.of(
            "id", saved.getId(),
            "likeCount", saved.getLikeCount(),
            "viewCount", saved.getViewCount()
        ));
    }

    @PostMapping("/{id}/view")
    public ResponseEntity<?> view(@PathVariable Long id) {
        Long safeId = Objects.requireNonNull(id, "id");
        AnonymousPost post = repo.findById(safeId).orElse(null);
        if (post == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Anonymous post not found"));
        }
        post.setViewCount(Math.max(0, post.getViewCount() + 1));
        AnonymousPost saved = repo.save(post);
        return ResponseEntity.ok(Map.of(
            "id", saved.getId(),
            "likeCount", saved.getLikeCount(),
            "viewCount", saved.getViewCount()
        ));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "description", required = false) String description
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is required");
        }
        if (description == null) {
            description = "";
        }
        String url;
        try {
            url = uploadService.upload(file);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("message", e.getMessage()));
        }

        AnonymousPost post = new AnonymousPost();
        post.setContentUrl(url);
        post.setDescription(description);
        String contentType = file.getContentType();
        post.setType(contentType != null && contentType.startsWith("video") ? "VIDEO" : "IMAGE");
        post.setApproved(false);

        try {
            AnonymousPost saved = repo.save(post);
            return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "contentUrl", saved.getContentUrl(),
                "description", saved.getDescription() == null ? "" : saved.getDescription(),
                "type", saved.getType(),
                "approved", saved.isApproved(),
                "createdAt", String.valueOf(saved.getCreatedAt())
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Anonymous post save failed: " + e.getMessage()));
        }
    }
}
