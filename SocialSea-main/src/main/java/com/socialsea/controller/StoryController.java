package com.socialsea.controller;

import com.socialsea.model.Follow;
import com.socialsea.model.Story;
import com.socialsea.model.User;
import com.socialsea.repository.FollowRepository;
import com.socialsea.repository.StoryRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.CloudinaryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequestMapping("/api/stories")
public class StoryController {

    private final StoryRepository storyRepo;
    private final UserRepository userRepo;
    private final FollowRepository followRepo;
    private final CloudinaryService cloudinaryService;

    public StoryController(
        StoryRepository storyRepo,
        UserRepository userRepo,
        FollowRepository followRepo,
        CloudinaryService cloudinaryService
    ) {
        this.storyRepo = storyRepo;
        this.userRepo = userRepo;
        this.followRepo = followRepo;
        this.cloudinaryService = cloudinaryService;
    }

    @GetMapping("/feed")
    public ResponseEntity<?> feed(Authentication auth) {
        LocalDateTime now = LocalDateTime.now();
        List<Story> stories;
        User currentUser = null;
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            currentUser = userRepo.findByEmail(auth.getName()).orElse(null);
        }

        if (currentUser == null) {
            stories = storyRepo.findByExpiresAtAfterOrderByCreatedAtDesc(now);
            stories = stories.stream()
                .filter(story -> isPublicStory(story.getPrivacy()))
                .toList();
        } else {
            List<User> followedUsers = followRepo.findByFollower(currentUser).stream()
                .map(Follow::getFollowing)
                .filter(Objects::nonNull)
                .toList();
            List<User> candidates = new ArrayList<>(followedUsers);
            candidates.add(currentUser);
            stories = storyRepo.findByUserInAndExpiresAtAfterOrderByCreatedAtDesc(candidates, now);
            User finalCurrentUser = currentUser;
            stories = stories.stream()
                .filter(story -> canViewStory(finalCurrentUser, story))
                .toList();
        }

        List<Map<String, Object>> payload = stories.stream()
            .map(this::toPayload)
            .toList();
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/media/{id}")
    public ResponseEntity<?> media(@PathVariable Long id, Authentication auth) {
        Story story = storyRepo.findById(id).orElse(null);
        if (story == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Story not found"));
        }
        LocalDateTime now = LocalDateTime.now();
        if (story.getExpiresAt() != null && story.getExpiresAt().isBefore(now)) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("message", "Story expired"));
        }

        User currentUser = null;
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            currentUser = userRepo.findByEmail(auth.getName()).orElse(null);
        }
        if (!canViewStory(currentUser, story)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not allowed"));
        }

        String url = story.getMediaUrl();
        if (url == null || url.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Story media missing"));
        }
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, url)
            .build();
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "storyPrivacy", required = false) String privacy,
        @RequestParam(value = "storyExpiresHours", required = false) String expiresHours,
        @RequestParam(value = "caption", required = false) String caption,
        @RequestParam(value = "storyText", required = false) String storyText,
        @RequestParam(value = "storyStyle", required = false) String storyStyle,
        @RequestParam(value = "storyTextStyle", required = false) String storyTextStyle,
        Authentication auth
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File is required"));
        }
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        User user = userRepo.findByEmail(auth.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "User not found"));
        }

        String url = cloudinaryService.upload(file);
        Story story = new Story();
        story.setUser(user);
        story.setMediaUrl(url);
        story.setMediaType(detectMediaType(file));
        story.setPrivacy(normalizePrivacy(privacy));
        story.setCaption(trimToNull(caption));
        story.setStoryText(trimToNull(storyText));
        story.setStoryStyle(trimToNull(storyStyle));
        story.setStoryTextStyle(trimToNull(storyTextStyle));
        story.setCreatedAt(LocalDateTime.now());
        story.setExpiresAt(LocalDateTime.now().plusHours(parseHours(expiresHours)));

        Story saved = storyRepo.save(story);
        return ResponseEntity.ok(toPayload(saved));
    }

    private boolean isPublicStory(String privacy) {
        String raw = String.valueOf(privacy == null ? "" : privacy).trim().toLowerCase();
        return raw.isEmpty() || raw.equals("public");
    }

    private boolean canViewStory(User viewer, Story story) {
        if (story == null) return false;
        String privacy = String.valueOf(story.getPrivacy() == null ? "" : story.getPrivacy()).trim().toLowerCase();
        if (privacy.isEmpty() || privacy.equals("public")) return true;
        if (viewer == null) return false;
        User owner = story.getUser();
        if (owner != null && viewer.getId() != null && viewer.getId().equals(owner.getId())) {
            return true;
        }
        if (privacy.equals("followers") || privacy.equals("close_friends")) {
            if (owner == null) return false;
            return followRepo.existsByFollowerAndFollowing(viewer, owner);
        }
        return false;
    }

    private Map<String, Object> toPayload(Story story) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", story.getId());
        payload.put("mediaUrl", story.getMediaUrl());
        payload.put("mediaType", story.getMediaType());
        payload.put("caption", story.getCaption());
        payload.put("storyText", story.getStoryText());
        payload.put("storyStyle", story.getStoryStyle());
        payload.put("storyTextStyle", story.getStoryTextStyle());
        payload.put("privacy", story.getPrivacy());
        payload.put("createdAt", toIso(story.getCreatedAt()));
        payload.put("expiresAt", toEpochMs(story.getExpiresAt()));
        payload.put("isVideo", story.getMediaType() != null && story.getMediaType().toLowerCase().contains("video"));
        if (story.getUser() != null) {
            payload.put("user", Map.of(
                "id", story.getUser().getId(),
                "email", story.getUser().getEmail(),
                "name", story.getUser().getEmail()
            ));
        }
        return payload;
    }

    private String detectMediaType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) return "file";
        if (contentType.startsWith("video")) return "video";
        if (contentType.startsWith("image")) return "image";
        if (contentType.startsWith("audio")) return "audio";
        return contentType;
    }

    private String normalizePrivacy(String privacy) {
        String raw = String.valueOf(privacy == null ? "" : privacy).trim().toLowerCase();
        if (raw.isEmpty()) return "public";
        if (raw.equals("followers")) return "followers";
        if (raw.equals("close_friends")) return "close_friends";
        return "public";
    }

    private long toEpochMs(LocalDateTime value) {
        if (value == null) return 0L;
        return value.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private String toIso(LocalDateTime value) {
        if (value == null) return null;
        return value.toString();
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

    private String trimToNull(String raw) {
        String value = raw == null ? "" : raw.trim();
        return value.isEmpty() ? null : value;
    }
}
