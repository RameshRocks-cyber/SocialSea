package com.socialsea.controller;

import com.socialsea.dto.PublicFeedDto;
import com.socialsea.dto.PublicReelDto;
import com.socialsea.model.Post;
import com.socialsea.model.User;
import com.socialsea.repository.FollowRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.ArrayList;
import java.time.Duration;

@RestController
@RequestMapping({"/api/clips", "/clips", "/api/reels", "/reels"})
@CrossOrigin(origins = {"https://socialsea.netlify.app", "https://socialsea.co.in", "https://www.socialsea.co.in", "http://localhost:5173", "http://127.0.0.1:5173", "http://43.205.229.211:5173"})
public class ReelController {
    private static final int HARD_MAX_ITEMS = 600;

    private final PostRepository postRepo;
    private final UserRepository userRepo;
    private final FollowRepository followRepo;

    @Value("${app.reels.max-items:180}")
    private int maxReelItems;

    @Value("${app.reels.cache-seconds:15}")
    private int reelsCacheSeconds;

    public ReelController(
            PostRepository postRepo,
            UserRepository userRepo,
            FollowRepository followRepo
    ) {
        this.postRepo = postRepo;
        this.userRepo = userRepo;
        this.followRepo = followRepo;
    }

    @GetMapping
    public ResponseEntity<List<PublicReelDto>> reels(
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "limit", required = false) Integer limit,
            Authentication auth
    ) {
        User viewer = (auth != null && auth.isAuthenticated())
                ? userRepo.findByEmail(auth.getName()).orElse(null)
                : null;
        Set<Long> allowedPrivateIds = resolveAllowedPrivateIds(viewer);
        int resolvedLimit = resolveLimit(size != null ? size : limit, maxReelItems, HARD_MAX_ITEMS);
        Pageable pageable = PageRequest.of(0, resolvedLimit);

        List<PublicReelDto> localItems = postRepo.findApprovedReelCandidates(pageable)
                .stream()
                .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                .filter(p -> canViewPost(viewer, allowedPrivateIds, p.getUser()))
                .map(PublicReelDto::fromPost)
                .toList();

        List<PublicReelDto> merged = new ArrayList<>(localItems);
        merged.sort(Comparator.comparing(this::safeCreatedAt).reversed());
        return ResponseEntity.ok()
            .cacheControl(privateCache(Math.max(1, reelsCacheSeconds)))
            .body(merged);
    }

    @GetMapping("/{postId}")
    public ResponseEntity<?> reelById(@PathVariable("postId") Long postId, Authentication auth) {
        User viewer = (auth != null && auth.isAuthenticated())
                ? userRepo.findByEmail(auth.getName()).orElse(null)
                : null;
        Set<Long> allowedPrivateIds = resolveAllowedPrivateIds(viewer);

        Long safeId = Objects.requireNonNull(postId, "postId");
        Optional<Post> postOpt = postRepo.findPostWithUserById(safeId);
        if (postOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Post not found"));
        }

        Post post = postOpt.get();
        if (!post.isApproved()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Post not found"));
        }
        if (!post.isReel()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Post not found"));
        }
        if (post.getMediaUrl() == null || post.getMediaUrl().isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Post not found"));
        }
        if (!canViewPost(viewer, allowedPrivateIds, post.getUser())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Post not found"));
        }

        return ResponseEntity.ok()
            .cacheControl(privateCache(Math.max(1, reelsCacheSeconds)))
            .body(PublicReelDto.fromPost(post));
    }

    private boolean canViewPost(User viewer, Set<Long> allowedPrivateIds, User owner) {
        if (owner == null) return false;
        if (!owner.isPrivateAccount()) return true;
        return viewer != null && owner.getId() != null && allowedPrivateIds.contains(owner.getId());
    }

    private LocalDateTime safeCreatedAt(PublicFeedDto item) {
        return item != null && item.getCreatedAt() != null ? item.getCreatedAt() : LocalDateTime.MIN;
    }

    private Set<Long> resolveAllowedPrivateIds(User viewer) {
        if (viewer == null || viewer.getId() == null) return Set.of();
        Set<Long> allowedPrivateIds = new HashSet<>();
        allowedPrivateIds.add(viewer.getId());
        followRepo.findFollowingIdsByFollowerId(viewer.getId())
            .stream()
            .filter(Objects::nonNull)
            .forEach(allowedPrivateIds::add);
        return allowedPrivateIds;
    }

    private int resolveLimit(Integer requested, int fallback, int hardMax) {
        int safeFallback = Math.max(1, Math.min(hardMax, fallback));
        if (requested == null) return safeFallback;
        return Math.max(1, Math.min(hardMax, requested));
    }

    private CacheControl privateCache(int seconds) {
        return CacheControl.maxAge(Duration.ofSeconds(seconds))
            .cachePrivate()
            .mustRevalidate();
    }
}

