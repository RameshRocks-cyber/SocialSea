package com.socialsea.controller;

import com.socialsea.dto.FeedItemDto;
import com.socialsea.model.Post;
import com.socialsea.model.User;
import com.socialsea.model.Story;
import com.socialsea.repository.FollowRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.StoryRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.AnonymousPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.Comparator;

@RestController
@RequestMapping({"/api/feed", "/feed"})
@CrossOrigin(origins = {"https://socialsea.netlify.app",
    "https://socialsea.co.in",
    "https://www.socialsea.co.in", "http://localhost:5173", "http://43.205.213.14:5173"})
@RequiredArgsConstructor
public class FeedController {

    private final AnonymousPostService anonymousPostService;
    private final PostRepository postRepo;
    private final UserRepository userRepo;
    private final FollowRepository followRepo;
    private final StoryRepository storyRepo;

    @Value("${app.feed.include-unapproved:false}")
    private boolean includeUnapproved;

    @GetMapping
    public ResponseEntity<?> feed(Authentication auth) {
        User viewer = (auth != null && auth.isAuthenticated())
                ? userRepo.findByEmail(auth.getName()).orElse(null)
                : null;
        Set<String> storyMediaUrls = storyRepo.findAll()
                .stream()
                .map(Story::getMediaUrl)
                .filter(url -> url != null && !url.isBlank())
                .collect(Collectors.toSet());
        Set<Long> allowedPrivateIds = new HashSet<>();
        if (viewer != null) {
            allowedPrivateIds.add(viewer.getId());
            followRepo.findByFollower(viewer).forEach(f -> {
                if (f.getFollowing() != null && f.getFollowing().getId() != null) {
                    allowedPrivateIds.add(f.getFollowing().getId());
                }
            });
        }

        List<FeedItemDto> normalPosts = postRepo.findAll()
                .stream()
                .filter(p -> !isStoryPost(p.getMediaUrl(), storyMediaUrls))
                .filter(p -> !p.isReel())
                .filter(p -> includeUnapproved || p.isApproved())
                .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                .filter(p -> canViewPost(viewer, allowedPrivateIds, p.getUser()))
                .map(FeedItemDto::fromEntity)
                .toList();

        List<FeedItemDto> merged = new ArrayList<>(normalPosts);
        merged.sort(Comparator.comparing(this::safeCreatedAt).reversed());
        return ResponseEntity.ok(merged);
    }

    @GetMapping("/videos")
    public ResponseEntity<?> videos(Authentication auth) {
        User viewer = (auth != null && auth.isAuthenticated())
                ? userRepo.findByEmail(auth.getName()).orElse(null)
                : null;
        Set<String> storyMediaUrls = storyRepo.findAll()
                .stream()
                .map(Story::getMediaUrl)
                .filter(url -> url != null && !url.isBlank())
                .collect(Collectors.toSet());

        Set<Long> allowedPrivateIds = new HashSet<>();
        if (viewer != null) {
            allowedPrivateIds.add(viewer.getId());
            followRepo.findByFollower(viewer).forEach(f -> {
                if (f.getFollowing() != null && f.getFollowing().getId() != null) {
                    allowedPrivateIds.add(f.getFollowing().getId());
                }
            });
        }

        List<FeedItemDto> localVideos = postRepo.findAll()
                .stream()
                .filter(p -> !isStoryPost(p.getMediaUrl(), storyMediaUrls))
                .filter(p -> !p.isReel())
                .filter(p -> includeUnapproved || p.isApproved())
                .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                .filter(p -> canViewPost(viewer, allowedPrivateIds, p.getUser()))
                .map(FeedItemDto::fromEntity)
                .filter(FeedItemDto::isVideo)
                .toList();

        List<FeedItemDto> merged = new ArrayList<>(localVideos);
        merged.sort(Comparator.comparing(this::safeCreatedAt).reversed());
        return ResponseEntity.ok(merged);
    }

    @GetMapping("/{postId}")
    public ResponseEntity<?> feedItemById(
            @PathVariable("postId") Long postId,
            Authentication auth
    ) {
        User viewer = (auth != null && auth.isAuthenticated())
                ? userRepo.findByEmail(auth.getName()).orElse(null)
                : null;

        Long safeId = Objects.requireNonNull(postId, "postId");
        Optional<Post> postOpt = postRepo.findById(safeId);
        if (postOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Post not found"));
        }

        Post post = postOpt.get();
        if (post.isReel()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Post not found"));
        }
        String mediaUrl = post.getMediaUrl();
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Post not found"));
        }

        if (!includeUnapproved && !post.isApproved()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Post not found"));
        }

        String trimmed = mediaUrl.trim();
        String bare = trimmed;
        int q = bare.indexOf('?');
        if (q > 0) bare = bare.substring(0, q);
        int h = bare.indexOf('#');
        if (h > 0) bare = bare.substring(0, h);
        if (storyRepo.existsByMediaUrl(trimmed) || storyRepo.existsByMediaUrl(bare)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Post not found"));
        }

        Set<Long> allowedPrivateIds = new HashSet<>();
        if (viewer != null) {
            allowedPrivateIds.add(viewer.getId());
            followRepo.findByFollower(viewer).forEach(f -> {
                if (f.getFollowing() != null && f.getFollowing().getId() != null) {
                    allowedPrivateIds.add(f.getFollowing().getId());
                }
            });
        }

        if (!canViewPost(viewer, allowedPrivateIds, post.getUser())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Post not found"));
        }

        return ResponseEntity.ok(FeedItemDto.fromEntity(post));
    }

    @GetMapping("/anonymous")
    public List<FeedItemDto> getAnonymousFeed() {
        return anonymousPostService.getApprovedFeed();
    }

    private boolean canViewPost(User viewer, Set<Long> allowedPrivateIds, User owner) {
        if (owner == null) return false;
        if (!owner.isPrivateAccount()) return true;
        return viewer != null && owner.getId() != null && allowedPrivateIds.contains(owner.getId());
    }

    private boolean isStoryPost(String mediaUrl, Set<String> storyMediaUrls) {
        if (mediaUrl == null || mediaUrl.isBlank()) return false;
        return storyMediaUrls.contains(mediaUrl);
    }

    private LocalDateTime safeCreatedAt(FeedItemDto item) {
        return item.getCreatedAt() == null ? LocalDateTime.MIN : item.getCreatedAt();
    }
}

