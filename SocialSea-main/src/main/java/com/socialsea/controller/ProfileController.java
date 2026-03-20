package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/profile")
@CrossOrigin(origins = {"https://socialsea.netlify.app","https://socialsea.co.in","https://www.socialsea.co.in","http://localhost:5173","http://127.0.0.1:5173"})
public class ProfileController {

    private final UserRepository userRepo;
    private final PostRepository postRepo;
    private final FollowRepository followRepo;

    public ProfileController(
        UserRepository userRepo,
        PostRepository postRepo,
        FollowRepository followRepo
    ) {
        this.userRepo = userRepo;
        this.postRepo = postRepo;
        this.followRepo = followRepo;
    }

    // User profile info
    @GetMapping("/{identifier}")
    public ResponseEntity<?> profile(@PathVariable String identifier, Authentication auth) {
        Optional<User> userOpt = resolveUser(identifier, auth);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found"));
        }
        User user = userOpt.get();

        long followers = followRepo.countByFollowing(user);
        long following = followRepo.countByFollower(user);
        long postsCount = postRepo.findByUser(user).stream()
                .filter(Post::isApproved)
                .count();

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getEmail());
        profile.put("email", user.getEmail());
        profile.put("name", user.getEmail());
        profile.put("bio", null);
        profile.put("profilePic", null);
        profile.put("profilePicUrl", null);
        profile.put("profileCompleted", true);
        profile.put("followers", followers);
        profile.put("following", following);
        profile.put("postsCount", postsCount);

        return ResponseEntity.ok(profile);
    }

    // User posts
    @GetMapping("/{identifier}/posts")
    public ResponseEntity<?> posts(@PathVariable String identifier, Authentication auth) {
        Optional<User> userOpt = resolveUser(identifier, auth);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found"));
        }

        List<Post> posts = postRepo.findByUser(userOpt.get())
                .stream()
                .filter(Post::isApproved)
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
                .map(this::toUserItem)
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
                .map(this::toUserItem)
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

        return userRepo.findByEmail(identifier);
    }

    private Map<String, Object> toUserItem(User user) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", user.getId());
        item.put("email", user.getEmail());
        item.put("name", user.getEmail());
        item.put("username", user.getEmail());
        item.put("profilePic", null);
        item.put("profilePicUrl", null);
        return item;
    }
}

