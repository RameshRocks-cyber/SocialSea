package com.socialsea.controller;

import com.socialsea.dto.FeedItemDto;
import com.socialsea.model.*;
import com.socialsea.repository.*;
import com.socialsea.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/profile")
@CrossOrigin(origins = {"https://socialsea.netlify.app",
    "https://socialsea.co.in",
    "https://www.socialsea.co.in", "http://localhost:5173", "http://43.205.213.14:5173"})
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepo;
    private final PostRepository postRepo;
    private final FollowRepository followRepo;
    private final EmergencyAlertRepository emergencyRepo;
    private final ProfileService profileService;

    // User profile info
    @GetMapping("/{identifier}")
    public ResponseEntity<?> profile(@PathVariable String identifier) {
        Optional<User> userOpt;

        if (identifier.matches("\\d+")) {
            userOpt = userRepo.findById(Long.parseLong(identifier));
        } else {
            userOpt = userRepo.findByEmail(identifier);
        }

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found"));
        }
        User user = userOpt.get();

        long followers = followRepo.countByFollowing(user);
        long following = followRepo.countByFollower(user);
        long postsCount = postRepo.countByUser(user);

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getEmail());
        profile.put("email", user.getEmail());
        profile.put("name", user.getName());
        profile.put("bio", user.getBio());
        profile.put("profilePic", user.getProfilePic());
        profile.put("profilePicUrl", user.getProfilePic());
        profile.put("profileCompleted", user.isProfileCompleted());
        profile.put("followers", followers);
        profile.put("following", following);
        profile.put("postsCount", postsCount);

        return ResponseEntity.ok(profile);
    }

    // ✅ My posts (Authenticated user)
    @GetMapping("/posts")
    public ResponseEntity<?> myPosts(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login required");
        }
        User user = userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Post> posts = postRepo.findByUser(user)
                .stream()
                .filter(Post::isApproved)
                .toList();
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/me")
    public ResponseEntity<?> myProfile(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login required");
        }

        User user = userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        long followers = followRepo.countByFollowing(user);
        long following = followRepo.countByFollower(user);
        long postsCount = postRepo.countByUser(user);

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getEmail());
        profile.put("email", user.getEmail());
        profile.put("name", user.getName());
        profile.put("bio", user.getBio());
        profile.put("profilePic", user.getProfilePic());
        profile.put("profilePicUrl", user.getProfilePic());
        profile.put("profileCompleted", user.isProfileCompleted());
        profile.put("followers", followers);
        profile.put("following", following);
        profile.put("postsCount", postsCount);

        return ResponseEntity.ok(profile);
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

    @GetMapping("/name/check")
    public ResponseEntity<?> checkName(
            @RequestParam(name = "name", defaultValue = "") String name,
            Authentication auth
    ) {
        Long myUserId = null;
        if (auth != null && auth.isAuthenticated()) {
            myUserId = userRepo.findByEmail(auth.getName()).map(User::getId).orElse(null);
        }
        return ResponseEntity.ok(profileService.checkNameAvailability(name, myUserId));
    }

    @GetMapping("/me/posts")
    public ResponseEntity<?> myPostsByMe(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login required");
        }

        User user = userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<FeedItemDto> posts = postRepo.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                .map(FeedItemDto::fromEntity)
                .toList();

        return ResponseEntity.ok(posts);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(
            @RequestParam(name = "q", defaultValue = "") String q,
            Authentication auth
    ) {
        String query = q == null ? "" : q.trim();
        if (query.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        Long myUserId = null;
        if (auth != null && auth.isAuthenticated()) {
            myUserId = userRepo.findByEmail(auth.getName()).map(User::getId).orElse(null);
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
                    item.put("profilePic", u.getProfilePic());
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

        long userId = userOpt.get().getId();
        List<FeedItemDto> posts = postRepo.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                .map(FeedItemDto::fromEntity)
                .toList();
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/{identifier}/followers")
    public ResponseEntity<?> followersList(@PathVariable String identifier, Authentication auth) {
        Optional<User> userOpt = resolveUser(identifier, auth);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found"));
        }

        List<Map<String, Object>> users = followRepo.findByFollowing(userOpt.get()).stream()
                .map(Follow::getFollower)
                .filter(u -> u != null && u.getId() != null)
                .map(u -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", u.getId());
                    item.put("email", u.getEmail());
                    item.put("name", (u.getName() != null && !u.getName().isBlank()) ? u.getName() : u.getEmail());
                    item.put("username", (u.getName() != null && !u.getName().isBlank()) ? u.getName() : u.getEmail());
                    item.put("profilePic", u.getProfilePic());
                    item.put("profilePicUrl", u.getProfilePic());
                    return item;
                })
                .distinct()
                .toList();

        return ResponseEntity.ok(users);
    }

    @GetMapping("/{identifier}/following")
    public ResponseEntity<?> followingList(@PathVariable String identifier, Authentication auth) {
        Optional<User> userOpt = resolveUser(identifier, auth);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found"));
        }

        List<Map<String, Object>> users = followRepo.findByFollower(userOpt.get()).stream()
                .map(Follow::getFollowing)
                .filter(u -> u != null && u.getId() != null)
                .map(u -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", u.getId());
                    item.put("email", u.getEmail());
                    item.put("name", (u.getName() != null && !u.getName().isBlank()) ? u.getName() : u.getEmail());
                    item.put("username", (u.getName() != null && !u.getName().isBlank()) ? u.getName() : u.getEmail());
                    item.put("profilePic", u.getProfilePic());
                    item.put("profilePicUrl", u.getProfilePic());
                    return item;
                })
                .distinct()
                .toList();

        return ResponseEntity.ok(users);
    }

    private Optional<User> resolveUser(String identifier, Authentication auth) {
        if ("me".equalsIgnoreCase(identifier)) {
            if (auth == null || !auth.isAuthenticated()) {
                return Optional.empty();
            }
            return userRepo.findByEmail(auth.getName());
        }

        if (identifier != null && identifier.matches("\\d+")) {
            return userRepo.findById(Long.parseLong(identifier));
        }

        return userRepo.findByEmail(identifier)
                .or(() -> userRepo.findByNameIgnoreCase(identifier));
    }

    @PostMapping("/setup")
    public ResponseEntity<?> setupProfile(
            @RequestParam(required = false) Long userId,
            @RequestParam String name,
            @RequestParam(required = false) MultipartFile profilePic,
            @RequestParam(required = false) String bio,
            Authentication auth
    ) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        User currentUser = userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long effectiveUserId = currentUser.getId();
        if (userId != null && !userId.equals(effectiveUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Cannot edit another user"));
        }

        try {
            Map<String, Object> updated = profileService.setupProfile(effectiveUserId, name, bio, profilePic);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            Map<String, Object> availability = profileService.checkNameAvailability(name, effectiveUserId);
            return ResponseEntity.badRequest().body(Map.of(
                    "message", e.getMessage(),
                    "available", availability.get("available"),
                    "suggestions", availability.get("suggestions")
            ));
        }
    }
}



