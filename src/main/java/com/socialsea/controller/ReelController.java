package com.socialsea.controller;

import com.socialsea.model.Post;
import com.socialsea.model.User;
import com.socialsea.repository.FollowRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.UserRepository;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/reels")
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
    public List<Post> reels(Authentication auth) {
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

        return postRepo.findAll()
                .stream()
                .filter(Post::isApproved)
                .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                .filter(p -> canViewPost(viewer, allowedPrivateIds, p.getUser()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
    }

    private boolean canViewPost(User viewer, Set<Long> allowedPrivateIds, User owner) {
        if (owner == null) return false;
        if (!owner.isPrivateAccount()) return true;
        return viewer != null && owner.getId() != null && allowedPrivateIds.contains(owner.getId());
    }
}

