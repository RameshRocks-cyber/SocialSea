package com.socialsea.controller;

import com.socialsea.dto.StoryDto;
import com.socialsea.model.Story;
import com.socialsea.model.User;
import com.socialsea.repository.StoryCommentRepository;
import com.socialsea.repository.StoryLikeRepository;
import com.socialsea.repository.StoryRepository;
import com.socialsea.repository.StoryViewRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.StoryService;
import com.socialsea.service.UploadService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.URI;

@RestController
@RequestMapping("/api/stories")
@CrossOrigin(origins = {
        "https://socialsea.netlify.app",
        "https://socialsea.co.in",
        "https://www.socialsea.co.in",
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://43.205.229.211:5173"
})
public class StoryController {
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final StoryService storyService;
    private final UserRepository userRepo;
    private final UploadService uploadService;
    private final StoryRepository storyRepo;
    private final StoryLikeRepository storyLikeRepo;
    private final StoryCommentRepository storyCommentRepo;
    private final StoryViewRepository storyViewRepo;

    public StoryController(
            StoryService storyService,
            UserRepository userRepo,
            UploadService uploadService,
            StoryRepository storyRepo,
            StoryLikeRepository storyLikeRepo,
            StoryCommentRepository storyCommentRepo,
            StoryViewRepository storyViewRepo
    ) {
        this.storyService = storyService;
        this.userRepo = userRepo;
        this.uploadService = uploadService;
        this.storyRepo = storyRepo;
        this.storyLikeRepo = storyLikeRepo;
        this.storyCommentRepo = storyCommentRepo;
        this.storyViewRepo = storyViewRepo;
    }

    @GetMapping("/feed")
    public ResponseEntity<?> feed(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User viewer = userRepo.findByEmail(auth.getName()).orElse(null);
        if (viewer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Session expired"));
        }
        List<StoryDto> stories = storyService.fetchFeed(viewer);
        return ResponseEntity.ok(stories);
    }

    @GetMapping("/mine")
    public ResponseEntity<?> mine(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User viewer = userRepo.findByEmail(auth.getName()).orElse(null);
        if (viewer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Session expired"));
        }
        List<Story> stories = storyRepo.findByUserOrderByCreatedAtDesc(viewer);
        List<StoryDto> result = stories.stream()
                .map(story -> storyService.toDtoWithStats(story, viewer))
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "storyPrivacy", required = false) String storyPrivacy,
            @RequestParam(value = "storyExpiresHours", required = false) String storyExpiresHours,
            @RequestParam(value = "storyText", required = false) String storyText,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestParam(value = "storyStyle", required = false) String storyStyle,
            @RequestParam(value = "storyTextStyle", required = false) String storyTextStyle,
            Authentication auth
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File is required"));
        }
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User user = userRepo.findByEmail(auth.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Session expired"));
        }

        String url;
        try {
            url = uploadService.upload(file);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
        }

        Story story = new Story();
        story.setMediaUrl(url);
        story.setCaption(caption != null ? caption : "");
        story.setStoryText(storyText != null ? storyText : "");
        story.setStoryStyle(storyStyle != null ? storyStyle : "");
        story.setStoryTextStyle(storyTextStyle != null ? storyTextStyle : "");
        story.setPrivacy(storyPrivacy != null ? storyPrivacy : "public");
        story.setUser(user);

        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
        if (storyExpiresHours != null) {
            try {
                long hours = Long.parseLong(storyExpiresHours);
                expiresAt = LocalDateTime.now().plusHours(Math.max(1, hours));
            } catch (Exception ignored) {
                // keep default
            }
        }
        story.setExpiresAt(expiresAt);

        Story saved = storyRepo.save(story);

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", saved.getId());
        payload.put("mediaUrl", saved.getMediaUrl());
        payload.put("storyUrl", saved.getMediaUrl());
        payload.put("isVideo", isVideoContentType(file.getContentType()));
        payload.put("caption", saved.getCaption());
        payload.put("storyText", saved.getStoryText());
        payload.put("privacy", saved.getPrivacy());
        payload.put("createdAt", formatIsoOffset(saved.getCreatedAt()));
        payload.put("expiresAt", formatIsoOffset(saved.getExpiresAt()));
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/media/{id}")
    public ResponseEntity<?> media(@PathVariable("id") Long id, Authentication auth) {
        if (id == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Missing story id"));
        }
        User viewer = null;
        if (auth != null && auth.isAuthenticated()) {
            viewer = userRepo.findByEmail(auth.getName()).orElse(null);
        }
        var story = storyService.findById(id);
        if (story == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Story not found"));
        }
        if (!storyService.canViewStory(story, viewer)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not allowed"));
        }
        String mediaUrl = story.getMediaUrl();
        if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Story media missing"));
        }
        String target = mediaUrl.trim();
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
        }
        if (!target.startsWith("/")) {
            target = "/" + target;
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteStory(@PathVariable("id") Long id, Authentication auth) {
        if (id == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Missing story id"));
        }
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User viewer = userRepo.findByEmail(auth.getName()).orElse(null);
        if (viewer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Session expired"));
        }

        Story story = storyRepo.findById(id).orElse(null);
        if (story == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Story not found"));
        }
        User owner = story.getUser();
        if (owner == null || owner.getId() == null || !owner.getId().equals(viewer.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not allowed"));
        }

        storyCommentRepo.deleteByStory(story);
        storyLikeRepo.deleteByStory(story);
        storyViewRepo.deleteByStory(story);
        storyRepo.delete(story);
        return ResponseEntity.ok(Map.of("ok", true, "deletedId", id));
    }

    @PostMapping("/{id}/delete")
    public ResponseEntity<?> deleteStoryViaPost(@PathVariable("id") Long id, Authentication auth) {
        return deleteStory(id, auth);
    }

    private String formatIsoOffset(LocalDateTime value) {
        if (value == null) return null;
        return value.atZone(ZoneId.systemDefault()).format(ISO_OFFSET);
    }

    private boolean isVideoContentType(String contentType) {
        return contentType != null && contentType.startsWith("video");
    }
}
