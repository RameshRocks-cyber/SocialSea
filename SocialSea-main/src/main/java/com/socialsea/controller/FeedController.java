package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/feed")
@CrossOrigin(origins = {"https://socialsea.netlify.app","https://socialsea.co.in","https://www.socialsea.co.in","http://localhost:5173","http://127.0.0.1:5173"})
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

    @GetMapping("/{id}")
    public ResponseEntity<?> feedItem(@PathVariable("id") Long id) {
        Optional<Post> postOpt = postRepo.findById(id);
        if (postOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Post not found");
        }
        Post post = postOpt.get();
        if (!post.isApproved()) {
            return ResponseEntity.status(404).body("Post not found");
        }
        return ResponseEntity.ok(post);
    }
}

