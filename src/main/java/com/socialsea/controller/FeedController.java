package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/feed")
@CrossOrigin
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
    public List<Post> feed(Authentication auth) {

        // Logged-in user
        User currentUser = userRepo.findByUsername(auth.getName()).orElseThrow();

        // Users I follow
        List<User> followedUsers = followRepo
            .findByFollower(currentUser)
            .stream()
            .map(Follow::getFollowing)
            .collect(Collectors.toList());

        // Include my own posts
        followedUsers.add(currentUser);

        // Posts from followed users
        return postRepo.findByUserIn(followedUsers);
    }
}
