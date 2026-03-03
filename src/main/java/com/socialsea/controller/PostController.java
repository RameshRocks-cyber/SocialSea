package com.socialsea.controller;

import com.socialsea.model.Post;
import com.socialsea.model.User;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.CloudinaryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostRepository postRepo;
    private final UserRepository userRepo;
    private final CloudinaryService cloudinaryService;

    public PostController(
        PostRepository postRepo,
        UserRepository userRepo,
        CloudinaryService cloudinaryService
    ) {
        this.postRepo = postRepo;
        this.userRepo = userRepo;
        this.cloudinaryService = cloudinaryService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
        @RequestParam("file") MultipartFile file,
        Authentication auth
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is required");
        }

        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login required");
        }

        User user = userRepo.findByEmail(auth.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Session expired. Please login again."));
        }

        String url;
        try {
            url = cloudinaryService.upload(file);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("message", e.getMessage()));
        }

        Post post = new Post();
        post.setMediaUrl(url);
        post.setReel(isVideo(file));
        post.setApproved(true);
        post.setUser(user);

        try {
            Post saved = postRepo.save(post);
            return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "mediaUrl", saved.getMediaUrl(),
                "reel", saved.isReel(),
                "approved", saved.isApproved(),
                "createdAt", String.valueOf(saved.getCreatedAt()),
                "userId", saved.getUser() != null ? saved.getUser().getId() : null
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Post save failed: " + e.getMessage()));
        }
    }

    private boolean isVideo(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("video");
    }
}
