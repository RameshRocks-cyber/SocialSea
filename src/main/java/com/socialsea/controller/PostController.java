package com.socialsea.controller;

import com.socialsea.model.Post;
import com.socialsea.model.Role;
import com.socialsea.model.Story;
import com.socialsea.model.User;
import com.socialsea.repository.CommentRepository;
import com.socialsea.repository.LikeRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.SavedPostRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final int MAX_MEDIA_TYPE_LEN = 40;
    private static final int MAX_ORIGINAL_FILE_NAME_LEN = 255;
    private static final double MAX_CLIP_DURATION_SECONDS = 120d;
    private static final double CLIP_DURATION_TOLERANCE_SECONDS = 0.35d;
    private static final int COPYRIGHT_MATCH_SAMPLE_LIMIT = 5;
    private static final String COPYRIGHT_STATUS_CLEAR = "clear";
    private static final String COPYRIGHT_STATUS_REVIEW = "review";
    private static final String COPYRIGHT_STATUS_BLOCKED = "blocked";

    private final PostRepository postRepo;
    private final UserRepository userRepo;
    private final CommentRepository commentRepo;
    private final LikeRepository likeRepo;
    private final SavedPostRepository savedPostRepo;
    private final UploadService uploadService;
    private final StoryRepository storyRepo;
    private final VideoEditingService videoEditingService;

    public PostController(
        PostRepository postRepo,
        UserRepository userRepo,
        CommentRepository commentRepo,
        LikeRepository likeRepo,
        SavedPostRepository savedPostRepo,
        UploadService uploadService,
        StoryRepository storyRepo,
        VideoEditingService videoEditingService
    ) {
        this.postRepo = postRepo;
        this.userRepo = userRepo;
        this.commentRepo = commentRepo;
        this.likeRepo = likeRepo;
        this.savedPostRepo = savedPostRepo;
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

    @PostMapping("/copyright-match")
    public ResponseEntity<?> copyrightMatch(
        @RequestParam("file") MultipartFile file,
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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Session expired. Please login again."));
        }

        String mediaType = normalizeMediaType(file.getContentType());
        long mediaSizeBytes = Math.max(0L, file.getSize());
        String mediaFingerprint = computeMediaFingerprint(file, mediaType, mediaSizeBytes);
        Map<String, Object> result = buildCopyrightMatchResult(mediaFingerprint, user.getId());
        result.put("mediaType", mediaType);
        result.put("mediaSizeBytes", mediaSizeBytes);
        return ResponseEntity.ok(result);
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
        String mediaType = normalizeMediaType(file.getContentType());
        long mediaSizeBytes = Math.max(0L, file.getSize());
        String mediaFingerprint = null;
        Map<String, Object> copyrightMatch = null;
        if (!wantsStory) {
            mediaFingerprint = computeMediaFingerprint(file, mediaType, mediaSizeBytes);
            copyrightMatch = buildCopyrightMatchResult(mediaFingerprint, user.getId());
            if (Boolean.TRUE.equals(copyrightMatch.get("blocked"))) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "Copyright match found. This file matches another creator's existing upload.",
                    "copyright", copyrightMatch
                ));
            }
        }

        try (VideoEditingService.ProcessingResult videoResult = (!wantsStory && isVideo(file))
            ? videoEditingService.prepareForUpload(file, videoSettings)
            : null) {

            MultipartFile mediaUploadFile = videoResult != null ? videoResult.getUploadFile() : file;
            MultipartFile generatedCoverFile = videoResult != null ? videoResult.getGeneratedCoverImage() : null;
            String normalizedSettings = videoResult != null
                ? videoResult.getNormalizedVideoSettings()
                : clip(clean(videoSettings), MAX_SETTINGS_LEN);
            boolean editsApplied = videoResult != null && videoResult.isEditsApplied();

            boolean videoFile = isVideo(mediaUploadFile);
            boolean wantsReel = isTruthy(isReel) || isTruthy(reel) || isReelType(type);
            boolean wantsLongVideo = isTruthy(isLongVideo) || isLongVideoType(type);
            if ((wantsReel || wantsLongVideo) && !videoFile) {
                return ResponseEntity.badRequest().body(Map.of("message", "Video file required for clips/videos"));
            }
            if (!wantsStory && wantsReel && videoFile) {
                double durationSeconds = videoEditingService.probeDurationSeconds(mediaUploadFile);
                if (durationSeconds > MAX_CLIP_DURATION_SECONDS + CLIP_DURATION_TOLERANCE_SECONDS) {
                    double roundedDurationSeconds = Math.round(Math.max(0d, durationSeconds) * 10d) / 10d;
                    return ResponseEntity.badRequest().body(Map.of(
                        "message", "Clips must be 2 minutes or less (120 seconds max).",
                        "maxSeconds", (int) MAX_CLIP_DURATION_SECONDS,
                        "durationSeconds", roundedDurationSeconds
                    ));
                }
            }

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

            String coverImageUrl = null;
            MultipartFile chosenCover = firstNonEmptyFile(coverImage, generatedCoverFile);
            if (chosenCover != null && !chosenCover.isEmpty()) {
                coverImageUrl = uploadService.upload(chosenCover);
            }

            String safeTitle = clip(clean(title), MAX_TITLE_LEN);
            String safeDescription = clip(clean(caption), MAX_DESC_LEN);
            String safeSettings = ensureVideoDistributionSettings(
                clip(clean(normalizedSettings), MAX_SETTINGS_LEN),
                videoFile,
                wantsReel,
                wantsLongVideo
            );

            Post post = new Post();
            post.setMediaUrl(mediaUrl);
            post.setReel(videoFile && wantsReel);
            post.setApproved(true);
            post.setUser(user);
            post.setTitle(safeTitle);
            post.setDescription(safeDescription);
            post.setVideoSettings(safeSettings);
            post.setCoverImageUrl(coverImageUrl);
            post.setMediaFingerprint(mediaFingerprint);
            post.setMediaType(clip(mediaType, MAX_MEDIA_TYPE_LEN));
            post.setMediaSizeBytes(mediaSizeBytes > 0 ? mediaSizeBytes : null);
            post.setOriginalFileName(clip(clean(file.getOriginalFilename()), MAX_ORIGINAL_FILE_NAME_LEN));

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
            payload.put("copyrightStatus", copyrightMatch != null ? copyrightMatch.get("status") : COPYRIGHT_STATUS_CLEAR);
            payload.put("copyrightExactMatchCount", copyrightMatch != null ? copyrightMatch.get("exactMatchCount") : 0);
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
    @Transactional
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

        commentRepo.deleteByPost(post);
        likeRepo.deleteByPost(post);
        savedPostRepo.deleteByPost(post);
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

    private Map<String, Object> buildCopyrightMatchResult(String mediaFingerprint, Long currentUserId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", COPYRIGHT_STATUS_CLEAR);
        payload.put("blocked", false);
        payload.put("summary", "No exact copyright match found.");
        payload.put("exactMatchCount", 0);
        payload.put("matches", List.of());
        payload.put("fingerprint", mediaFingerprint);
        payload.put("fingerprintShort", shortenFingerprint(mediaFingerprint));

        String normalizedFingerprint = clean(mediaFingerprint);
        if (normalizedFingerprint == null) {
            payload.put("summary", "Could not fingerprint the uploaded file.");
            return payload;
        }

        List<Post> exactMatches = postRepo.findTop10ByMediaFingerprintOrderByCreatedAtDesc(normalizedFingerprint);
        if (exactMatches.isEmpty()) {
            return payload;
        }

        boolean hasForeignMatch = exactMatches.stream().anyMatch((post) -> {
            Long ownerId = post.getUser() != null ? post.getUser().getId() : null;
            return ownerId != null && !Objects.equals(ownerId, currentUserId);
        });
        List<Map<String, Object>> matchRows = exactMatches.stream()
            .limit(COPYRIGHT_MATCH_SAMPLE_LIMIT)
            .map((post) -> toCopyrightMatchRow(post, currentUserId))
            .toList();

        payload.put("exactMatchCount", exactMatches.size());
        payload.put("matches", matchRows);

        if (hasForeignMatch) {
            payload.put("status", COPYRIGHT_STATUS_BLOCKED);
            payload.put("blocked", true);
            payload.put("summary", "Exact match found in another creator's upload. Publishing is blocked.");
            return payload;
        }

        payload.put("status", COPYRIGHT_STATUS_REVIEW);
        payload.put("blocked", false);
        payload.put("summary", "Exact match found in your own previous uploads.");
        return payload;
    }

    private Map<String, Object> toCopyrightMatchRow(Post post, Long currentUserId) {
        Map<String, Object> row = new LinkedHashMap<>();
        Long ownerId = post.getUser() != null ? post.getUser().getId() : null;
        boolean isOwner = ownerId != null && Objects.equals(ownerId, currentUserId);
        row.put("ownership", isOwner ? "self" : "other");
        row.put("ownerLabel", isOwner ? "You" : "Another creator");
        row.put("surface", post.isReel() ? "clip" : "post");
        row.put("uploadedAt", formatIsoOffset(post.getCreatedAt()));
        row.put("postId", isOwner ? post.getId() : null);
        return row;
    }

    private String shortenFingerprint(String fingerprint) {
        String normalized = clean(fingerprint);
        if (normalized == null) return null;
        if (normalized.length() <= 12) return normalized;
        return normalized.substring(0, 12);
    }

    private String normalizeMediaType(String contentType) {
        String normalized = clean(contentType);
        if (normalized == null) return "application/octet-stream";
        return clip(normalized.toLowerCase(), MAX_MEDIA_TYPE_LEN);
    }

    private String computeMediaFingerprint(
        MultipartFile file,
        String mediaType,
        long mediaSizeBytes
    ) {
        if (file == null || file.isEmpty()) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(String.valueOf(mediaType == null ? "" : mediaType).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(String.valueOf(mediaSizeBytes).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');

            try (InputStream stream = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException algorithmError) {
            throw new IllegalStateException("SHA-256 is not available in the runtime", algorithmError);
        } catch (Exception ioError) {
            throw new RuntimeException("Unable to fingerprint uploaded media", ioError);
        }
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

    private String ensureVideoDistributionSettings(
        String rawSettings,
        boolean videoFile,
        boolean wantsReel,
        boolean wantsLongVideo
    ) {
        if (!videoFile || wantsReel) return rawSettings;

        String distributionSurface = wantsLongVideo ? "video_feed" : "post_feed";
        String uploadContext = wantsLongVideo ? "long_video" : "post_feed";
        String uploadType = wantsLongVideo ? "long_video" : "post_video";

        String markerJson = "\"distributionSurface\":\"" + distributionSurface + "\"," +
            "\"uploadContext\":\"" + uploadContext + "\"," +
            "\"uploadType\":\"" + uploadType + "\"";

        if (rawSettings == null || rawSettings.isBlank()) {
            String generated = "{" + markerJson + "}";
            return generated.length() <= MAX_SETTINGS_LEN ? generated : null;
        }

        String trimmed = rawSettings.trim();
        String lower = trimmed.toLowerCase();
        if (
            lower.contains("\"distributionsurface\"") ||
            lower.contains("\"uploadsurface\"") ||
            lower.contains("\"uploadcontext\"")
        ) {
            return rawSettings;
        }

        if (!(trimmed.startsWith("{") && trimmed.endsWith("}"))) {
            return rawSettings;
        }

        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        String merged = inner.isEmpty() ? "{" + markerJson + "}" : "{" + inner + "," + markerJson + "}";
        if (merged.length() > MAX_SETTINGS_LEN) return rawSettings;
        return merged;
    }

    private String formatIsoOffset(LocalDateTime value) {
        if (value == null) return null;
        return value.atZone(ZoneId.systemDefault()).format(ISO_OFFSET);
    }
}
