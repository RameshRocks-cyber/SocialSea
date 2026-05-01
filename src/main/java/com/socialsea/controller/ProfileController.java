package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import com.socialsea.service.ProfileService;
import com.socialsea.util.MediaUrlUtils;
import com.socialsea.util.UrlUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/profile")
@CrossOrigin(origins = {"https://socialsea.netlify.app",
    "https://socialsea.co.in",
    "https://www.socialsea.co.in", "http://localhost:5173", "http://43.205.213.14:5173"})
@RequiredArgsConstructor
public class ProfileController {

    private static final Pattern LANGUAGE_TAG_PATTERN =
            Pattern.compile("^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$");

    private final UserRepository userRepo;
    private final PostRepository postRepo;
    private final FollowRepository followRepo;
    private final FollowRequestRepository followRequestRepo;
    private final LikeRepository likeRepo;
    private final CommentRepository commentRepo;
    private final SavedPostRepository savedPostRepo;
    private final EmergencyAlertRepository emergencyRepo;
    private final ProfileService profileService;
    private final StoryRepository storyRepo;

    // User profile info
    @GetMapping("/{identifier}")
    public ResponseEntity<?> profile(
            @PathVariable String identifier,
            Authentication auth,
            HttpServletRequest request
    ) {
        Optional<User> userOpt = resolveUser(identifier, auth);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found"));
        }
        User user = userOpt.get();
        User viewer = resolveAuthenticatedUser(auth).orElse(null);
        boolean canViewContent = canViewProfileContent(viewer, user);
        String followStatus = resolveFollowStatus(viewer, user);

        long followers = followRepo.countByFollowing(user);
        long following = followRepo.countByFollower(user);
        long postsCount = 0;
        long videosCount = 0;
        if (canViewContent) {
            List<Post> visiblePosts = postRepo.findByUser(user)
                    .stream()
                    .filter(Post::isApproved)
                    .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                    .filter(p -> !isStoryPost(p.getMediaUrl()))
                    .toList();
            postsCount = visiblePosts.size();
            videosCount = visiblePosts.stream().filter(this::isVideoPost).count();
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getEmail());
        profile.put("email", canViewContent ? user.getEmail() : null);
        profile.put("name", user.getName());
        profile.put("bio", canViewContent ? user.getBio() : "");
        String profilePicUrl = UrlUtils.toAbsoluteUrl(request, user.getProfilePic());
        String coverPhotoUrl = UrlUtils.toAbsoluteUrl(request, user.getCoverPhoto());
        profile.put("profilePic", profilePicUrl);
        profile.put("profilePicUrl", profilePicUrl);
        profile.put("coverUrl", coverPhotoUrl);
        profile.put("coverPhoto", coverPhotoUrl);
        profile.put("coverPhotoUrl", coverPhotoUrl);
        profile.put("profileCoverUrl", coverPhotoUrl);
        profile.put("profileCompleted", user.isProfileCompleted());
        profile.put("followers", followers);
        profile.put("following", following);
        profile.put("postsCount", postsCount);
        profile.put("postCount", postsCount);
        profile.put("totalPosts", postsCount);
        profile.put("videosCount", videosCount);
        profile.put("videoCount", videosCount);
        profile.put("totalVideos", videosCount);
        profile.put("privateAccount", user.isPrivateAccount());
        profile.put("longVideosEnabled", user.isLongVideosEnabled());
        profile.put("canViewContent", canViewContent);
        profile.put("followStatus", followStatus);
        profile.put("isFollowing", "FOLLOWING".equalsIgnoreCase(followStatus));

        return ResponseEntity.ok(profile);
    }

    // ✅ My posts (Authenticated user)
    @GetMapping("/posts")
    public ResponseEntity<?> myPosts(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login required");
        }
        User user = resolveAuthenticatedUser(auth)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Map<String, Object>> posts = postRepo.findByUser(user)
                .stream()
                .filter(Post::isApproved)
                .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                .filter(p -> !isStoryPost(p.getMediaUrl()))
                .map(this::toShortFeedPostPayload)
                .toList();
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/me")
    public ResponseEntity<?> myProfile(Authentication auth, HttpServletRequest request) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login required");
        }

        User user = resolveAuthenticatedUser(auth).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Session expired"));
        }

        long followers = followRepo.countByFollowing(user);
        long following = followRepo.countByFollower(user);
        List<Post> visiblePosts = postRepo.findByUser(user)
                .stream()
                .filter(Post::isApproved)
                .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                .filter(p -> !isStoryPost(p.getMediaUrl()))
                .toList();
        long postsCount = visiblePosts.size();
        long videosCount = visiblePosts.stream().filter(this::isVideoPost).count();

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getEmail());
        profile.put("email", user.getEmail());
        profile.put("name", user.getName());
        profile.put("bio", user.getBio());
        String profilePicUrl = UrlUtils.toAbsoluteUrl(request, user.getProfilePic());
        String coverPhotoUrl = UrlUtils.toAbsoluteUrl(request, user.getCoverPhoto());
        profile.put("profilePic", profilePicUrl);
        profile.put("profilePicUrl", profilePicUrl);
        profile.put("coverUrl", coverPhotoUrl);
        profile.put("coverPhoto", coverPhotoUrl);
        profile.put("coverPhotoUrl", coverPhotoUrl);
        profile.put("profileCoverUrl", coverPhotoUrl);
        profile.put("profileCompleted", user.isProfileCompleted());
        profile.put("followers", followers);
        profile.put("following", following);
        profile.put("postsCount", postsCount);
        profile.put("postCount", postsCount);
        profile.put("totalPosts", postsCount);
        profile.put("videosCount", videosCount);
        profile.put("videoCount", videosCount);
        profile.put("totalVideos", videosCount);
        profile.put("privateAccount", user.isPrivateAccount());
        profile.put("trafficAlertsEnabled", user.isTrafficAlertsEnabled());
        profile.put("longVideosEnabled", user.isLongVideosEnabled());
        profile.put("preferredLanguage", user.getPreferredLanguage());
        profile.put("notificationVoice", user.getNotificationVoice());
        profile.put("ambulanceDriverApproved", user.isAmbulanceDriverApproved());
        profile.put("canViewContent", true);
        profile.put("followStatus", "FOLLOWING");
        profile.put("isFollowing", true);

        return ResponseEntity.ok(profile);
    }

    @PostMapping("/me/privacy")
    public ResponseEntity<?> updatePrivacy(@RequestBody Map<String, Object> body, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User user = resolveAuthenticatedUser(auth)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Object raw = body.get("privateAccount");
        boolean next = raw instanceof Boolean
                ? (Boolean) raw
                : Boolean.parseBoolean(String.valueOf(raw));
        user.setPrivateAccount(next);
        userRepo.save(user);
        return ResponseEntity.ok(Map.of("privateAccount", user.isPrivateAccount()));
    }

    @PostMapping("/me/traffic-alerts")
    public ResponseEntity<?> updateTrafficAlerts(@RequestBody Map<String, Object> body, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User user = resolveAuthenticatedUser(auth)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Object raw = body != null ? body.get("enabled") : null;
        boolean next = raw instanceof Boolean
                ? (Boolean) raw
                : Boolean.parseBoolean(String.valueOf(raw));
        user.setTrafficAlertsEnabled(next);
        userRepo.save(user);
        return ResponseEntity.ok(Map.of("trafficAlertsEnabled", user.isTrafficAlertsEnabled()));
    }

    @GetMapping("/me/long-videos")
    public ResponseEntity<?> myLongVideosSetting(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User user = resolveAuthenticatedUser(auth)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(Map.of("longVideosEnabled", user.isLongVideosEnabled()));
    }

    @PostMapping("/me/long-videos")
    public ResponseEntity<?> updateLongVideosSetting(@RequestBody(required = false) Map<String, Object> body, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User user = resolveAuthenticatedUser(auth)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Object raw = body != null ? (body.get("enabled") != null
                ? body.get("enabled")
                : (body.get("longVideosEnabled") != null
                    ? body.get("longVideosEnabled")
                    : body.get("longVideoEnabled"))) : null;

        boolean next = raw instanceof Boolean
                ? (Boolean) raw
                : Boolean.parseBoolean(String.valueOf(raw));

        user.setLongVideosEnabled(next);
        userRepo.save(user);
        return ResponseEntity.ok(Map.of("longVideosEnabled", user.isLongVideosEnabled()));
    }

    @GetMapping("/me/language")
    public ResponseEntity<?> myLanguage(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User user = resolveAuthenticatedUser(auth)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(Map.of("preferredLanguage", user.getPreferredLanguage()));
    }

    @PostMapping("/me/language")
    public ResponseEntity<?> updateLanguage(@RequestBody(required = false) Map<String, Object> body, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User user = resolveAuthenticatedUser(auth)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Object raw = body != null ? (body.get("preferredLanguage") != null
                ? body.get("preferredLanguage")
                : (body.get("language") != null ? body.get("language") : body.get("lang"))) : null;

        String next = raw == null ? "" : String.valueOf(raw).trim();
        next = next.replace('_', '-');
        if (next.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Missing preferredLanguage"));
        }
        if (next.length() > 16 || !LANGUAGE_TAG_PATTERN.matcher(next).matches()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid language tag"));
        }

        user.setPreferredLanguage(next);
        userRepo.save(user);
        return ResponseEntity.ok(Map.of("preferredLanguage", user.getPreferredLanguage()));
    }

    @GetMapping("/me/notification-voice")
    public ResponseEntity<?> myNotificationVoice(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User user = resolveAuthenticatedUser(auth)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(Map.of("notificationVoice", user.getNotificationVoice()));
    }

    @PostMapping("/me/notification-voice")
    public ResponseEntity<?> updateNotificationVoice(
            @RequestBody(required = false) Map<String, Object> body,
            Authentication auth
    ) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User user = resolveAuthenticatedUser(auth)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Object raw = body != null ? (body.get("notificationVoice") != null
                ? body.get("notificationVoice")
                : (body.get("voice") != null ? body.get("voice") : body.get("gender"))) : null;

        String next = raw == null ? "" : String.valueOf(raw).trim();
        if (next.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Missing notificationVoice"));
        }

        next = next.toLowerCase();
        if ("m".equals(next)) next = "male";
        if ("f".equals(next)) next = "female";

        if (!"male".equals(next) && !"female".equals(next)) {
            return ResponseEntity.badRequest().body(Map.of("message", "notificationVoice must be 'male' or 'female'"));
        }

        user.setNotificationVoice(next);
        userRepo.save(user);
        return ResponseEntity.ok(Map.of("notificationVoice", user.getNotificationVoice()));
    }

    @PostMapping("/me/posts/cleanup-stories")
    @Transactional
    public ResponseEntity<?> cleanupStoryPosts(
            @RequestBody Map<String, Object> body,
            Authentication auth
    ) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User user = resolveAuthenticatedUser(auth)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Object raw = body != null ? body.get("mediaUrls") : null;
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return ResponseEntity.ok(Map.of("deleted", 0));
        }
        Set<String> targets = new HashSet<>();
        for (Object entry : list) {
            if (entry == null) continue;
            String url = String.valueOf(entry).trim();
            if (url.isEmpty()) continue;
            targets.add(url);
            int q = url.indexOf('?');
            if (q > 0) targets.add(url.substring(0, q));
            int h = url.indexOf('#');
            if (h > 0) targets.add(url.substring(0, h));
        }
        if (targets.isEmpty()) {
            return ResponseEntity.ok(Map.of("deleted", 0));
        }

        List<Post> posts = postRepo.findByUser(user);
        int deleted = 0;
        for (Post post : posts) {
            String url = post.getMediaUrl();
            if (url == null || url.isBlank()) continue;
            String trimmed = url.trim();
            String bare = trimmed;
            int q = bare.indexOf('?');
            if (q > 0) bare = bare.substring(0, q);
            int h = bare.indexOf('#');
            if (h > 0) bare = bare.substring(0, h);
            if (targets.contains(trimmed) || targets.contains(bare)) {
                // ensure dependent entities are removed first (FK constraints)
                commentRepo.deleteByPost(post);
                likeRepo.deleteByPost(post);
                savedPostRepo.deleteByPost(post);
                postRepo.delete(post);
                deleted++;
            }
        }

        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    @GetMapping({"/live-recordings", "/me/live-recordings"})
    public ResponseEntity<?> myLiveRecordings(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        List<Map<String, Object>> items = emergencyRepo
                .findByReporterEmailOrderByStartedAtDesc(auth.getName())
                .stream()
                .filter(a -> a.getMediaUrl() != null && !a.getMediaUrl().trim().isEmpty())
                .map(a -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("alertId", a.getId());
                    item.put("mediaUrl", a.getMediaUrl());
                    item.put("startedAt", a.getStartedAt());
                    item.put("endedAt", a.getEndedAt());
                    item.put("durationMs", a.getDurationMs());
                    item.put("latitude", a.getLatitude());
                    item.put("longitude", a.getLongitude());
                    return item;
                })
                .toList();

        return ResponseEntity.ok(items);
    }

    @DeleteMapping({"/live-recordings/{alertId}", "/me/live-recordings/{alertId}"})
    public ResponseEntity<?> deleteLiveRecording(@PathVariable Long alertId, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        Long safeAlertId = Objects.requireNonNull(alertId, "alertId");
        Optional<EmergencyAlert> alertOpt = emergencyRepo.findById(safeAlertId);
        if (alertOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Recording not found"));
        }

        EmergencyAlert alert = alertOpt.get();
        if (!auth.getName().equalsIgnoreCase(alert.getReporterEmail())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not allowed"));
        }

        emergencyRepo.delete(alert);
        return ResponseEntity.ok(Map.of("ok", true, "deletedId", safeAlertId));
    }

    @GetMapping("/name/check")
    public ResponseEntity<?> checkName(
            @RequestParam(name = "name", defaultValue = "") String name,
            Authentication auth
    ) {
        Long myUserId = null;
        if (auth != null && auth.isAuthenticated()) {
            myUserId = resolveAuthenticatedUser(auth).map(User::getId).orElse(null);
        }
        return ResponseEntity.ok(profileService.checkNameAvailability(name, myUserId));
    }

    @GetMapping("/me/posts")
    public ResponseEntity<?> myPostsByMe(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login required");
        }

        User user = resolveAuthenticatedUser(auth)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Map<String, Object>> posts = postRepo.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .filter(Post::isApproved)
                .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                .filter(p -> !isStoryPost(p.getMediaUrl()))
                .map(this::toShortFeedPostPayload)
                .toList();

        return ResponseEntity.ok(posts);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(
            @RequestParam(name = "q", defaultValue = "") String q,
            Authentication auth,
            HttpServletRequest request
    ) {
        String query = q == null ? "" : q.trim();
        if (query.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        Long myUserId = null;
        if (auth != null && auth.isAuthenticated()) {
            myUserId = resolveAuthenticatedUser(auth).map(User::getId).orElse(null);
        }

        Long finalMyUserId = myUserId;
        List<Map<String, Object>> users = userRepo
                .findTop20ByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(query, query)
                .stream()
                .filter(u -> finalMyUserId == null || !u.getId().equals(finalMyUserId))
                .map(u -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", u.getId());
                    item.put("email", u.getEmail());
                    item.put("name", u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getEmail());
                    String profilePicUrl = UrlUtils.toAbsoluteUrl(request, u.getProfilePic());
                    item.put("profilePic", profilePicUrl);
                    item.put("profilePicUrl", profilePicUrl);
                    return item;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
    }

    @GetMapping("/{identifier}/posts")
    public ResponseEntity<?> posts(@PathVariable String identifier, Authentication auth) {
        Optional<User> userOpt = resolveUser(identifier, auth);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found"));
        }

        User target = userOpt.get();
        User viewer = resolveAuthenticatedUser(auth).orElse(null);
        if (!canViewProfileContent(viewer, target)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "This account is private"));
        }

        long userId = target.getId();
        List<Map<String, Object>> posts = postRepo.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(Post::isApproved)
                .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                .filter(p -> !isStoryPost(p.getMediaUrl()))
                .map(this::toShortFeedPostPayload)
                .toList();
        return ResponseEntity.ok(posts);
    }

    private boolean isStoryPost(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) return false;
        List<Story> stories = storyRepo.findAll();
        for (Story story : stories) {
            String storyUrl = story.getMediaUrl();
            if (storyUrl != null && !storyUrl.isBlank() && storyUrl.equals(mediaUrl)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> toShortFeedPostPayload(Post post) {
        boolean video = isVideoPost(post);
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", post.getId());
        payload.put("mediaUrl", post.getMediaUrl());
        payload.put("contentUrl", post.getMediaUrl());
        payload.put("reel", post.isReel());
        payload.put("originalReel", post.isReel());
        payload.put("type", video ? "VIDEO" : "IMAGE");
        payload.put("isVideo", video);
        payload.put("video", video);
        payload.put("approved", post.isApproved());
        payload.put("createdAt", post.getCreatedAt());
        payload.put("user", post.getUser());
        if (post.getUser() != null) {
            payload.put("userId", post.getUser().getId());
            String displayName = (post.getUser().getName() != null && !post.getUser().getName().isBlank())
                    ? post.getUser().getName()
                    : post.getUser().getEmail();
            payload.put("username", displayName);
            payload.put("profilePic", post.getUser().getProfilePic());
        }
        return payload;
    }

    private boolean isVideoPost(Post post) {
        if (post == null) return false;
        return post.isReel() || MediaUrlUtils.isLikelyVideo(post.getMediaUrl());
    }

    @GetMapping("/{identifier}/followers")
    public ResponseEntity<?> followersList(
            @PathVariable String identifier,
            Authentication auth,
            HttpServletRequest request
    ) {
        Optional<User> userOpt = resolveUser(identifier, auth);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found"));
        }

        User target = userOpt.get();
        User viewer = resolveAuthenticatedUser(auth).orElse(null);
        if (!canViewProfileContent(viewer, target)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "This account is private"));
        }

        List<User> users = followRepo.findByFollowing(target).stream()
                .map(Follow::getFollower)
                .filter(u -> u != null && u.getId() != null)
                .toList();
        return ResponseEntity.ok(uniqueUserItemsById(request, users));
    }

    @GetMapping("/{identifier}/following")
    public ResponseEntity<?> followingList(
            @PathVariable String identifier,
            Authentication auth,
            HttpServletRequest request
    ) {
        Optional<User> userOpt = resolveUser(identifier, auth);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found"));
        }

        User target = userOpt.get();
        User viewer = resolveAuthenticatedUser(auth).orElse(null);
        if (!canViewProfileContent(viewer, target)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "This account is private"));
        }

        List<User> users = followRepo.findByFollower(target).stream()
                .map(Follow::getFollowing)
                .filter(u -> u != null && u.getId() != null)
                .toList();
        return ResponseEntity.ok(uniqueUserItemsById(request, users));
    }

    private boolean canViewProfileContent(User viewer, User owner) {
        if (owner == null) return false;
        if (!owner.isPrivateAccount()) return true;
        if (viewer != null && owner.getId().equals(viewer.getId())) return true;
        return viewer != null && followRepo.existsByFollowerAndFollowing(viewer, owner);
    }

    private String resolveFollowStatus(User viewer, User owner) {
        if (viewer == null || owner == null) return "NONE";
        if (viewer.getId().equals(owner.getId())) return "FOLLOWING";
        if (followRepo.existsByFollowerAndFollowing(viewer, owner)) return "FOLLOWING";
        if (followRequestRepo.existsBySenderAndReceiverAndStatus(viewer, owner, "PENDING")) return "REQUESTED";
        return "NONE";
    }

    private Optional<User> resolveUser(String identifier, Authentication auth) {
        String clean = normalizeIdentifier(identifier);

        if ("me".equalsIgnoreCase(clean)) {
            return resolveAuthenticatedUser(auth);
        }

        if (clean.matches("\\d+")) {
            return userRepo.findById(Long.parseLong(clean));
        }

        return userRepo.findByEmailIgnoreCase(clean)
                .or(() -> userRepo.findByNameIgnoreCase(clean));
    }

    private String normalizeIdentifier(String identifier) {
        if (identifier == null) return "";
        String clean = identifier.trim();
        try {
            clean = URLDecoder.decode(clean, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // Keep raw identifier when URL decoding fails.
        }

        while (!clean.isEmpty() && (clean.startsWith("[") || clean.startsWith("\"") || clean.startsWith("'"))) {
            clean = clean.substring(1).trim();
        }
        while (!clean.isEmpty() && (clean.endsWith("]") || clean.endsWith("\"") || clean.endsWith("'") || clean.endsWith(","))) {
            clean = clean.substring(0, clean.length() - 1).trim();
        }
        return clean;
    }

    private List<Map<String, Object>> uniqueUserItemsById(HttpServletRequest request, List<User> users) {
        Map<Long, Map<String, Object>> unique = new LinkedHashMap<>();
        for (User user : users) {
            if (user == null || user.getId() == null) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("id", user.getId());
            item.put("email", user.getEmail());
            item.put("name", (user.getName() != null && !user.getName().isBlank()) ? user.getName() : user.getEmail());
            item.put("username", (user.getName() != null && !user.getName().isBlank()) ? user.getName() : user.getEmail());
            String profilePicUrl = UrlUtils.toAbsoluteUrl(request, user.getProfilePic());
            item.put("profilePic", profilePicUrl);
            item.put("profilePicUrl", profilePicUrl);
            unique.putIfAbsent(user.getId(), item);
        }
        return List.copyOf(unique.values());
    }

    private Optional<User> resolveAuthenticatedUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        String identifier = String.valueOf(auth.getName()).trim();
        if (identifier.isEmpty()) {
            return Optional.empty();
        }
        return userRepo.findByEmailIgnoreCase(identifier)
                .or(() -> userRepo.findByNameIgnoreCase(identifier));
    }

    @PostMapping("/setup")
    public ResponseEntity<?> setupProfile(
            @RequestParam(required = false) Long userId,
            @RequestParam String name,
            @RequestParam(required = false) MultipartFile profilePic,
            @RequestParam(required = false) MultipartFile coverPhoto,
            @RequestParam(required = false) MultipartFile cover,
            @RequestParam(required = false) MultipartFile coverImage,
            @RequestParam(required = false) String bio,
            Authentication auth,
            HttpServletRequest request
    ) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        User currentUser = resolveAuthenticatedUser(auth)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long effectiveUserId = currentUser.getId();
        if (userId != null && !userId.equals(effectiveUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Cannot edit another user"));
        }

        try {
            MultipartFile effectiveCover = firstNonEmptyFile(coverPhoto, cover, coverImage);
            Map<String, Object> updated = profileService.setupProfile(effectiveUserId, name, bio, profilePic, effectiveCover);
            Map<String, Object> response = new HashMap<>(updated);
            Object raw = updated.get("profilePic");
            String profilePicUrl = UrlUtils.toAbsoluteUrl(request, raw == null ? null : String.valueOf(raw));
            response.put("profilePic", profilePicUrl == null ? "" : profilePicUrl);
            response.put("profilePicUrl", profilePicUrl == null ? "" : profilePicUrl);
            Object rawCover = updated.get("coverPhoto");
            String coverPhotoUrl = UrlUtils.toAbsoluteUrl(request, rawCover == null ? null : String.valueOf(rawCover));
            response.put("coverUrl", coverPhotoUrl == null ? "" : coverPhotoUrl);
            response.put("coverPhoto", coverPhotoUrl == null ? "" : coverPhotoUrl);
            response.put("coverPhotoUrl", coverPhotoUrl == null ? "" : coverPhotoUrl);
            response.put("profileCoverUrl", coverPhotoUrl == null ? "" : coverPhotoUrl);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> availability = profileService.checkNameAvailability(name, effectiveUserId);
            return ResponseEntity.badRequest().body(Map.of(
                    "message", e.getMessage(),
                    "available", availability.get("available"),
                    "suggestions", availability.get("suggestions")
            ));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? "Failed to update profile"
                    : e.getMessage();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
        }
    }

    private MultipartFile firstNonEmptyFile(MultipartFile... files) {
        if (files == null) {
            return null;
        }
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                return file;
            }
        }
        return null;
    }
}



