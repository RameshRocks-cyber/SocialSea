package com.socialsea.controller;

import com.socialsea.model.Post;
import com.socialsea.model.Role;
import com.socialsea.model.Story;
import com.socialsea.model.User;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.StoryRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.UploadService;
import com.socialsea.service.VideoEditingService;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/posts")
public class PostController {
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final int MAX_TITLE_LEN = 240;
    private static final int MAX_DESC_LEN = 3000;
    private static final int MAX_SETTINGS_LEN = 150000;

    private final PostRepository postRepo;
    private final UserRepository userRepo;
    private final UploadService uploadService;
    private final StoryRepository storyRepo;
    private final VideoEditingService videoEditingService;

    public PostController(
        PostRepository postRepo,
        UserRepository userRepo,
        UploadService uploadService,
        StoryRepository storyRepo,
        VideoEditingService videoEditingService
    ) {
        this.postRepo = postRepo;
        this.userRepo = userRepo;
        this.uploadService = uploadService;
        this.storyRepo = storyRepo;
        this.videoEditingService = videoEditingService;
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
        @RequestParam(value = "title", required = false) String title,
        @RequestParam(value = "videoSettings", required = false) String videoSettings,
        @RequestParam(value = "coverImage", required = false) MultipartFile coverImage,
        @RequestParam(value = "isReel", required = false) String isReel,
        @RequestParam(value = "reel", required = false) String reel,
        @RequestParam(value = "isLongVideo", required = false) String isLongVideo,
        @RequestParam(value = "type", required = false) String type,
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

        boolean wantsStory = isTruthy(isStory);
        try (VideoEditingService.ProcessingResult videoResult = (!wantsStory && isVideo(file))
            ? videoEditingService.prepareForUpload(file, videoSettings)
            : null) {

            MultipartFile mediaUploadFile = videoResult != null ? videoResult.getUploadFile() : file;
            MultipartFile generatedCoverFile = videoResult != null ? videoResult.getGeneratedCoverImage() : null;
            String normalizedSettings = videoResult != null
                ? videoResult.getNormalizedVideoSettings()
                : clip(clean(videoSettings), MAX_SETTINGS_LEN);
            boolean editsApplied = videoResult != null && videoResult.isEditsApplied();

            String mediaUrl = uploadService.upload(mediaUploadFile);

            if (wantsStory) {
                Story story = new Story();
                story.setMediaUrl(mediaUrl);
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

            boolean videoFile = isVideo(mediaUploadFile);
            boolean wantsReel = isTruthy(isReel) || isTruthy(reel) || isReelType(type);
            boolean wantsLongVideo = isTruthy(isLongVideo) || isLongVideoType(type);
            if ((wantsReel || wantsLongVideo) && !videoFile) {
                return ResponseEntity.badRequest().body(Map.of("message", "Video file required for clips/long videos"));
            }

            String coverImageUrl = null;
            MultipartFile chosenCover = firstNonEmptyFile(coverImage, generatedCoverFile);
            if (chosenCover != null && !chosenCover.isEmpty()) {
                coverImageUrl = uploadService.upload(chosenCover);
            }

            String safeTitle = clip(clean(title), MAX_TITLE_LEN);
            String safeDescription = clip(clean(caption), MAX_DESC_LEN);
            String safeSettings = clip(clean(normalizedSettings), MAX_SETTINGS_LEN);

            Post post = new Post();
            post.setMediaUrl(mediaUrl);
            post.setReel(videoFile && wantsReel);
            post.setApproved(true);
            post.setUser(user);
            post.setTitle(safeTitle);
            post.setDescription(safeDescription);
            post.setVideoSettings(safeSettings);
            post.setCoverImageUrl(coverImageUrl);

            Post saved = postRepo.save(post);

            Map<String, Object> payload = new HashMap<>();
            payload.put("id", saved.getId());
            payload.put("mediaUrl", saved.getMediaUrl());
            payload.put("contentUrl", saved.getMediaUrl());
            payload.put("coverImageUrl", saved.getCoverImageUrl());
            payload.put("coverImage", saved.getCoverImageUrl());
            payload.put("reel", saved.isReel());
            payload.put("approved", saved.isApproved());
            payload.put("title", saved.getTitle());
            payload.put("description", saved.getDescription());
            payload.put("videoSettings", saved.getVideoSettings());
            payload.put("createdAt", String.valueOf(saved.getCreatedAt()));
            payload.put("userId", saved.getUser() != null ? saved.getUser().getId() : null);
            payload.put("isVideo", videoFile);
            payload.put("video", videoFile);
            payload.put("editsApplied", editsApplied);
            return ResponseEntity.ok(payload);
        } catch (IllegalStateException e) {
            String message = clean(e.getMessage());
            if (message == null) message = "Video processing failed";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
        } catch (RuntimeException e) {
            String message = clean(e.getMessage());
            if (message == null) message = "Upload failed";
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", message));
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
        return contentType != null && contentType.toLowerCase().startsWith("video");
    }

    private MultipartFile firstNonEmptyFile(MultipartFile... files) {
        if (files == null) return null;
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) return file;
        }
        return null;
    }

    private boolean isTruthy(String value) {
        if (value == null) return false;
        String normalized = value.trim().toLowerCase();
        return normalized.equals("true") || normalized.equals("1") || normalized.equals("yes") || normalized.equals("y");
    }

    private boolean isReelType(String value) {
        if (value == null) return false;
        String normalized = value.trim().toLowerCase();
        return normalized.equals("reel") || normalized.equals("reels") || normalized.equals("short") || normalized.equals("short_video");
    }

    private boolean isLongVideoType(String value) {
        if (value == null) return false;
        String normalized = value.trim().toLowerCase();
        return normalized.equals("long_video") || normalized.equals("long-video") || normalized.equals("long") || normalized.equals("watch");
    }

    private String clean(String raw) {
        if (raw == null) return null;
        String out = raw.trim();
        return out.isEmpty() ? null : out;
    }

    private String clip(String raw, int maxLen) {
        if (raw == null) return null;
        if (raw.length() <= maxLen) return raw;
        return raw.substring(0, maxLen);
    }

    private String formatIsoOffset(LocalDateTime value) {
        if (value == null) return null;
        return value.atZone(ZoneId.systemDefault()).format(ISO_OFFSET);
    }
}

