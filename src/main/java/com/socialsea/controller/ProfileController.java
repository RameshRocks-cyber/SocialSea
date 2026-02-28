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

@RestController
@RequestMapping("/api/profile")
@CrossOrigin(origins = {"https://socialsea.netlify.app", "http://localhost:5173", "http://13.234.110.186:5173"})
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepo;
    private final PostRepository postRepo;
    private final FollowRepository followRepo;
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
        profile.put("username", user.getEmail());
        profile.put("email", user.getEmail());
        profile.put("name", user.getName() != null ? user.getName() : user.getEmail());
        profile.put("bio", user.getBio());
        profile.put("profilePic", user.getProfilePic());
        profile.put("profilePicUrl", user.getProfilePic());
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

    // User posts by id (frontend path: /api/profile/{id}/posts)
    @GetMapping("/{id}/posts")
    public ResponseEntity<?> posts(@PathVariable long id) {
        if (!userRepo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found"));
        }

        List<FeedItemDto> posts = postRepo.findByUserIdOrderByCreatedAtDesc(id)
                .stream()
                .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                .map(FeedItemDto::fromEntity)
                .toList();
        return ResponseEntity.ok(posts);
    }

    @PostMapping("/setup")
    public ResponseEntity<?> setupProfile(
            @RequestParam(required = false) Long userId,
            @RequestParam String name,
            @RequestParam(required = false) MultipartFile profilePic,
            @RequestParam(required = false) String bio
    ) {
        if (userId == null) {
            return ResponseEntity.badRequest().body("UserId missing");
        }
        profileService.setupProfile(userId, name, bio, profilePic);
        return ResponseEntity.ok().build();
    }
}
