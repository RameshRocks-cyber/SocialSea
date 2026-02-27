package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/feed")
@CrossOrigin("https://socialsea.netlify.app")
public class FeedController {

    private final UserRepository userRepo;
    private final FollowRepository followRepo;
    private final PostRepository postRepo;

    public FeedController(
        UserRepository userRepo,
        FollowRepository followRepo,
        PostRepository postRepo
    ) {
        this.userRepo = userRepo;
        this.followRepo = followRepo;
        this.postRepo = postRepo;
    }

    @GetMapping
    public ResponseEntity<?> feed(Authentication auth) {

        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        // Logged-in user
        User currentUser = userRepo.findByEmail(auth.getName()).orElse(null);
        if (currentUser == null) {
            return ResponseEntity.status(404).body("User not found");
        }

        // Users I follow
        List<User> followedUsers = followRepo
            .findByFollower(currentUser)
            .stream()
            .map(Follow::getFollowing)
            .collect(Collectors.toList());

        // Include my own posts
        followedUsers.add(currentUser);

        // Posts from followed users
        List<Post> posts = postRepo.findByUserIn(followedUsers)
                .stream()
                .filter(Post::isApproved)
                .collect(Collectors.toList());

        return ResponseEntity.ok(posts);
    }
}
