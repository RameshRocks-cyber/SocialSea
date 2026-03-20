package com.socialsea.controller;

import com.socialsea.model.Post;
import com.socialsea.model.Role;
import com.socialsea.model.User;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.StoryRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.model.Story;
import com.socialsea.service.CloudinaryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostRepository postRepo;
    private final UserRepository userRepo;
    private final StoryRepository storyRepo;
    private final CloudinaryService cloudinaryService;

    public PostController(
        PostRepository postRepo,
        UserRepository userRepo,
        StoryRepository storyRepo,
        CloudinaryService cloudinaryService
    ) {
        this.postRepo = postRepo;
        this.userRepo = userRepo;
        this.storyRepo = storyRepo;
        this.cloudinaryService = cloudinaryService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "isStory", required = false) String isStory,
        @RequestParam(value = "storyPrivacy", required = false) String storyPrivacy,
        @RequestParam(value = "storyExpiresHours", required = false) String storyExpiresHours,
        @RequestParam(value = "caption", required = false) String caption,
        @RequestParam(value = "storyText", required = false) String storyText,
        @RequestParam(value = "storyStyle", required = false) String storyStyle,
        @RequestParam(value = "storyTextStyle", required = false) String storyTextStyle,
        Authentication auth
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is required");
        }

        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login required");
        }

        User user = userRepo.findByEmail(auth.getName()).orElseThrow();
        String url = cloudinaryService.upload(file);

        boolean treatAsStory = isTruthy(isStory);
        if (treatAsStory) {
            Story story = new Story();
            story.setUser(user);
            story.setMediaUrl(url);
            story.setMediaType(isVideo(file) ? "video" : "image");
            story.setPrivacy(normalizePrivacy(storyPrivacy));
            story.setCaption(trimToNull(caption));
            story.setStoryText(trimToNull(storyText));
            story.setStoryStyle(trimToNull(storyStyle));
            story.setStoryTextStyle(trimToNull(storyTextStyle));
            story.setCreatedAt(java.time.LocalDateTime.now());
            story.setExpiresAt(java.time.LocalDateTime.now().plusHours(parseHours(storyExpiresHours)));

            Story savedStory = storyRepo.save(story);
            return ResponseEntity.ok(Map.of(
                "id", savedStory.getId(),
                "mediaUrl", savedStory.getMediaUrl(),
                "mediaType", savedStory.getMediaType(),
                "caption", savedStory.getCaption(),
                "storyText", savedStory.getStoryText(),
                "storyStyle", savedStory.getStoryStyle(),
                "storyTextStyle", savedStory.getStoryTextStyle(),
                "privacy", savedStory.getPrivacy(),
                "createdAt", String.valueOf(savedStory.getCreatedAt()),
                "expiresAt", savedStory.getExpiresAt() != null ? savedStory.getExpiresAt().toString() : null
            ));
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

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(
        @PathVariable("id") Long id,
        Authentication auth
    ) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        User currentUser = userRepo.findByEmail(auth.getName()).orElse(null);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Session expired. Please login again."));
        }

        Optional<Post> postOpt = postRepo.findById(id);
        if (postOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Post not found"));
        }

        Post post = postOpt.get();
        Long ownerId = post.getUser() != null ? post.getUser().getId() : null;
        boolean isOwner = ownerId != null && ownerId.equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.SUPER_ADMIN;

        if (!isOwner && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not allowed"));
        }

        postRepo.delete(post);
        return ResponseEntity.ok(Map.of("ok", true, "deletedId", id));
    }

    @PostMapping("/{id}/delete")
    public ResponseEntity<?> deletePostViaPost(
        @PathVariable("id") Long id,
        Authentication auth
    ) {
        return deletePost(id, auth);
    }

    private boolean isVideo(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("video");
    }

    private boolean isTruthy(String raw) {
        if (raw == null) return false;
        String value = raw.trim().toLowerCase();
        return value.equals("true") || value.equals("1") || value.equals("yes");
    }

    private long parseHours(String raw) {
        if (raw == null) return 24;
        try {
            long hours = Long.parseLong(raw.trim());
            if (hours <= 0) return 24;
            return Math.min(hours, 72);
        } catch (Exception e) {
            return 24;
        }
    }

    private String normalizePrivacy(String privacy) {
        String value = privacy == null ? "" : privacy.trim().toLowerCase();
        if (value.isEmpty()) return "public";
        if (value.equals("followers")) return "followers";
        if (value.equals("close_friends")) return "close_friends";
        return "public";
    }

    private String trimToNull(String raw) {
        String value = raw == null ? "" : raw.trim();
        return value.isEmpty() ? null : value;
    }
}
