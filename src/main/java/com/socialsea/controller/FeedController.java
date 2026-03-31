package com.socialsea.controller;

import com.socialsea.dto.FeedItemDto;
import com.socialsea.model.User;
import com.socialsea.model.Story;
import com.socialsea.repository.FollowRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.StoryRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.AnonymousPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/feed")
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
                .filter(p -> !isStoryPost(p.getMediaUrl()))
                .filter(p -> includeUnapproved || p.isApproved())
                .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                .filter(p -> canViewPost(viewer, allowedPrivateIds, p.getUser()))
                .map(FeedItemDto::fromEntity)
                .toList();
        return ResponseEntity.ok(normalPosts);
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
}

