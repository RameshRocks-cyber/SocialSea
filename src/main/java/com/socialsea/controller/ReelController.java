package com.socialsea.controller;

import com.socialsea.model.Post;
import com.socialsea.model.User;
import com.socialsea.repository.FollowRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.util.MediaUrlUtils;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping({"/api/reels", "/reels"})
@CrossOrigin(origins = {"https://socialsea.netlify.app", "https://socialsea.co.in", "https://www.socialsea.co.in", "http://localhost:5173", "http://127.0.0.1:5173", "http://43.205.213.14:5173"})
public class ReelController {

    private final PostRepository postRepo;
    private final UserRepository userRepo;
    private final FollowRepository followRepo;

    public ReelController(PostRepository postRepo, UserRepository userRepo, FollowRepository followRepo) {
        this.postRepo = postRepo;
        this.userRepo = userRepo;
        this.followRepo = followRepo;
    }

    @GetMapping
    public List<Map<String, Object>> reels(Authentication auth) {
        User viewer = (auth != null && auth.isAuthenticated())
                ? userRepo.findByEmail(auth.getName()).orElse(null)
                : null;
        Set<Long> allowedPrivateIds = new HashSet<>();
        if (viewer != null) {
            allowedPrivateIds.add(viewer.getId());
            followRepo.findByFollower(viewer).forEach(f -> {
                if (f.getFollowing() != null && f.getFollowing().getId() != null) {
                    allowedPrivateIds.add(f.getFollowing().getId());
                }
            });
        }

        // Short videos feed intentionally includes every approved media post (not only reel-flagged posts).
        return postRepo.findAll()
                .stream()
                .filter(Post::isApproved)
                .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                .filter(p -> canViewPost(viewer, allowedPrivateIds, p.getUser()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toReelPayload)
                .toList();
    }

    @GetMapping("/{postId}")
    public ResponseEntity<?> reelById(@PathVariable("postId") Long postId, Authentication auth) {
        User viewer = (auth != null && auth.isAuthenticated())
                ? userRepo.findByEmail(auth.getName()).orElse(null)
                : null;
        Set<Long> allowedPrivateIds = new HashSet<>();
        if (viewer != null) {
            allowedPrivateIds.add(viewer.getId());
            followRepo.findByFollower(viewer).forEach(f -> {
                if (f.getFollowing() != null && f.getFollowing().getId() != null) {
                    allowedPrivateIds.add(f.getFollowing().getId());
                }
            });
        }

        Long safeId = Objects.requireNonNull(postId, "postId");
        Optional<Post> postOpt = postRepo.findById(safeId);
        if (postOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Post not found"));
        }

        Post post = postOpt.get();
        if (!post.isApproved()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Post not found"));
        }
        if (post.getMediaUrl() == null || post.getMediaUrl().isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Post not found"));
        }
        if (!canViewPost(viewer, allowedPrivateIds, post.getUser())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Post not found"));
        }

        return ResponseEntity.ok(toReelPayload(post));
    }

    private boolean canViewPost(User viewer, Set<Long> allowedPrivateIds, User owner) {
        if (owner == null) return false;
        if (!owner.isPrivateAccount()) return true;
        return viewer != null && owner.getId() != null && allowedPrivateIds.contains(owner.getId());
    }

    private Map<String, Object> toReelPayload(Post post) {
        boolean video = post.isReel() || MediaUrlUtils.isLikelyVideo(post.getMediaUrl());
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", post.getId());
        payload.put("mediaUrl", post.getMediaUrl());
        payload.put("contentUrl", post.getMediaUrl());
        // Mark every item in /api/reels as part of short-video feed so image posts are not filtered out.
        payload.put("reel", true);
        payload.put("originalReel", post.isReel());
        payload.put("type", video ? "VIDEO" : "IMAGE");
        payload.put("isVideo", video);
        payload.put("approved", post.isApproved());
        payload.put("createdAt", post.getCreatedAt());
        payload.put("user", post.getUser());
        return payload;
    }
}

