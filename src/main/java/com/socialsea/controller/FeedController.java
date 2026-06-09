package com.socialsea.controller;

import com.socialsea.dto.PublicFeedDto;
import com.socialsea.model.Post;
import com.socialsea.model.User;
import com.socialsea.repository.FollowRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.StoryRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.AnonymousPostService;
import com.socialsea.util.MediaUrlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
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
import java.time.LocalDateTime;
import java.time.Duration;

@RestController
@RequestMapping({"/api/feed", "/feed"})
@CrossOrigin(origins = {"https://socialsea.netlify.app",
    "https://socialsea.co.in",
    "https://www.socialsea.co.in", "http://localhost:5173", "http://127.0.0.1:5173", "http://43.205.229.211:5173"})
@RequiredArgsConstructor
public class FeedController {
    private static final int HARD_MAX_ITEMS = 600;

    private final AnonymousPostService anonymousPostService;
    private final PostRepository postRepo;
    private final UserRepository userRepo;
    private final FollowRepository followRepo;
    private final StoryRepository storyRepo;

    @Value("${app.feed.include-unapproved:false}")
    private boolean includeUnapproved;

    @Value("${app.feed.max-items:240}")
    private int maxFeedItems;

    @Value("${app.feed.max-video-items:180}")
    private int maxVideoItems;

    @Value("${app.feed.cache-seconds:20}")
    private int feedCacheSeconds;

    @Value("${app.feed.by-id-cache-seconds:45}")
    private int feedByIdCacheSeconds;

    @Value("${app.feed.anonymous-cache-seconds:60}")
    private int anonymousFeedCacheSeconds;

    @GetMapping
    public ResponseEntity<?> feed(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "limit", required = false) Integer limit,
            Authentication auth
    ) {
        User viewer = (auth != null && auth.isAuthenticated())
                ? userRepo.findByEmail(auth.getName()).orElse(null)
                : null;

        Set<String> storyMediaUrls = activeStoryMediaUrls();
        Set<Long> allowedPrivateIds = resolveAllowedPrivateIds(viewer);
        int resolvedLimit = resolveLimit(size != null ? size : limit, maxFeedItems, HARD_MAX_ITEMS);
        int resolvedPage = Math.max(0, page != null ? page : 0);
        Pageable pageable = PageRequest.of(resolvedPage, Math.min(HARD_MAX_ITEMS, resolvedLimit + 1));

        List<Post> candidates = includeUnapproved
            ? postRepo.findFeedCandidates(pageable)
            : postRepo.findApprovedFeedCandidates(pageable);

        List<PublicFeedDto> normalPosts = candidates
                .stream()
                .filter(p -> !isStoryPost(p.getMediaUrl(), storyMediaUrls))
                .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                .filter(p -> canViewPost(viewer, allowedPrivateIds, p.getUser()))
                .map(PublicFeedDto::fromEntity)
                .limit(resolvedLimit)
                .toList();

        boolean hasNext = candidates.size() > resolvedLimit;

        return ResponseEntity.ok()
            .cacheControl(privateCache(Math.max(1, feedCacheSeconds)))
            .body(feedPage(normalPosts, resolvedPage, resolvedLimit, hasNext));
    }

    @GetMapping("/videos")
    public ResponseEntity<?> videos(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "limit", required = false) Integer limit,
            Authentication auth
    ) {
        User viewer = (auth != null && auth.isAuthenticated())
                ? userRepo.findByEmail(auth.getName()).orElse(null)
                : null;

        Set<String> storyMediaUrls = activeStoryMediaUrls();
        Set<Long> allowedPrivateIds = resolveAllowedPrivateIds(viewer);
        int resolvedLimit = resolveLimit(size != null ? size : limit, maxVideoItems, HARD_MAX_ITEMS);
        int resolvedPage = Math.max(0, page != null ? page : 0);
        Pageable pageable = PageRequest.of(resolvedPage, Math.min(HARD_MAX_ITEMS, resolvedLimit + 1));

        List<Post> candidates = includeUnapproved
            ? postRepo.findFeedCandidates(pageable)
            : postRepo.findApprovedFeedCandidates(pageable);

        List<PublicFeedDto> localVideos = candidates
                .stream()
                .filter(p -> !isStoryPost(p.getMediaUrl(), storyMediaUrls))
                .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                .filter(p -> canViewPost(viewer, allowedPrivateIds, p.getUser()))
                .filter(this::isVideoFeedPost)
                .map(PublicFeedDto::fromEntity)
                .limit(resolvedLimit)
                .toList();

        boolean hasNext = candidates.size() > resolvedLimit;

        return ResponseEntity.ok()
            .cacheControl(privateCache(Math.max(1, feedCacheSeconds)))
            .body(feedPage(localVideos, resolvedPage, resolvedLimit, hasNext));
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
        Optional<Post> postOpt = postRepo.findPostWithUserById(safeId);
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

        Set<Long> allowedPrivateIds = resolveAllowedPrivateIds(viewer);

        if (!canViewPost(viewer, allowedPrivateIds, post.getUser())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Post not found"));
        }

        return ResponseEntity.ok()
            .cacheControl(privateCache(Math.max(1, feedByIdCacheSeconds)))
            .body(PublicFeedDto.fromEntity(post));
    }

    @GetMapping("/anonymous")
    public ResponseEntity<List<PublicFeedDto>> getAnonymousFeed() {
        return ResponseEntity.ok()
            .cacheControl(publicCache(Math.max(1, anonymousFeedCacheSeconds)))
            .body(anonymousPostService.getApprovedFeed());
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

    private boolean isVideoFeedPost(Post post) {
        if (post == null) return false;
        if (post.isReel()) return false;
        if (!MediaUrlUtils.isLikelyVideo(post.getMediaUrl())) return false;

        String settings = post.getVideoSettings();
        if (settings == null || settings.isBlank()) return false;
        String normalized = settings.toLowerCase().replace(" ", "").replace("-", "_");
        return normalized.contains("\"distributionsurface\":\"video_feed\"")
            || normalized.contains("\"uploadsurface\":\"video_feed\"")
            || normalized.contains("\"uploadcontext\":\"long_video\"")
            || normalized.contains("\"uploadtype\":\"long_video\"")
            || normalized.contains("\"type\":\"long_video\"");
    }

    private Set<String> activeStoryMediaUrls() {
        return storyRepo.findActiveMediaUrls(LocalDateTime.now())
            .stream()
            .filter(url -> url != null && !url.isBlank())
            .collect(Collectors.toSet());
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

    private Map<String, Object> feedPage(List<PublicFeedDto> content, int page, int size, boolean hasNext) {
        return Map.of(
            "content", content,
            "page", Math.max(0, page),
            "size", Math.max(1, size),
            "hasNext", hasNext
        );
    }

    private CacheControl privateCache(int seconds) {
        return CacheControl.maxAge(Duration.ofSeconds(seconds))
            .cachePrivate()
            .mustRevalidate();
    }

    private CacheControl publicCache(int seconds) {
        return CacheControl.maxAge(Duration.ofSeconds(seconds))
            .cachePublic()
            .mustRevalidate();
    }
}

