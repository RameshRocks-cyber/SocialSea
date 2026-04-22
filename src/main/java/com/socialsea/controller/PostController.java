package com.socialsea.controller;

import com.socialsea.model.Post;
import com.socialsea.model.Role;
import com.socialsea.model.User;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.StoryRepository;
import com.socialsea.model.Story;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.CloudinaryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

@RestController
@RequestMapping("/api/posts")
public class PostController {
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final PostRepository postRepo;
    private final UserRepository userRepo;
    private final CloudinaryService cloudinaryService;
    private final StoryRepository storyRepo;

    public PostController(
        PostRepository postRepo,
        UserRepository userRepo,
        CloudinaryService cloudinaryService,
        StoryRepository storyRepo
    ) {
        this.postRepo = postRepo;
        this.userRepo = userRepo;
        this.cloudinaryService = cloudinaryService;
        this.storyRepo = storyRepo;
    }

    @GetMapping({"/create-options", "/upload-options"})
    public ResponseEntity<?> createOptions(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        User user = userRepo.findByEmail(auth.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Session expired. Please login again."));
        }

        return ResponseEntity.ok(Map.of(
            "longVideosEnabled", user.isLongVideosEnabled(),
            "showLongVideoOption", user.isLongVideosEnabled()
        ));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "isStory", required = false) String isStory,
        @RequestParam(value = "storyPrivacy", required = false) String storyPrivacy,
        @RequestParam(value = "storyExpiresHours", required = false) String storyExpiresHours,
        @RequestParam(value = "storyText", required = false) String storyText,
        @RequestParam(value = "caption", required = false) String caption,
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

        try {
            boolean wantsStory = isStory != null && isStory.equalsIgnoreCase("true");
            if (wantsStory) {
                Story story = new Story();
                story.setMediaUrl(url);
                story.setCaption(caption != null ? caption : "");
                story.setStoryText(storyText != null ? storyText : "");
                story.setStoryStyle(storyStyle != null ? storyStyle : "");
                story.setStoryTextStyle(storyTextStyle != null ? storyTextStyle : "");
                story.setPrivacy(storyPrivacy != null ? storyPrivacy : "public");
                story.setUser(user);
                if (storyExpiresHours != null) {
                    try {
                        long hours = Long.parseLong(storyExpiresHours);
                        story.setExpiresAt(LocalDateTime.now().plusHours(Math.max(1, hours)));
                    } catch (Exception ignored) {
                        story.setExpiresAt(LocalDateTime.now().plusHours(24));
                    }
                } else {
                    story.setExpiresAt(LocalDateTime.now().plusHours(24));
                }

                Story savedStory = storyRepo.save(story);
                return ResponseEntity.ok(Map.of(
                    "id", savedStory.getId(),
                    "mediaUrl", savedStory.getMediaUrl(),
                    "storyUrl", savedStory.getMediaUrl(),
                    "isVideo", isVideo(file),
                    "caption", savedStory.getCaption(),
                    "storyText", savedStory.getStoryText(),
                    "privacy", savedStory.getPrivacy(),
                    "createdAt", formatIsoOffset(savedStory.getCreatedAt()),
                    "expiresAt", formatIsoOffset(savedStory.getExpiresAt())
                ));
            }

            Post post = new Post();
            post.setMediaUrl(url);
            post.setReel(isVideo(file));
            post.setApproved(true);
            post.setUser(user);
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

        Long safeId = Objects.requireNonNull(id, "id");
        Optional<Post> postOpt = postRepo.findById(safeId);
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
        return ResponseEntity.ok(Map.of("ok", true, "deletedId", safeId));
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

    private String formatIsoOffset(LocalDateTime value) {
        if (value == null) return null;
        return value.atZone(ZoneId.systemDefault()).format(ISO_OFFSET);
    }
}
