package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
@CrossOrigin("https://socialsea.netlify.app")
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
    @GetMapping("/{username}")
    public ProfileResponse profile(@PathVariable String username) {

        User user = userRepo.findByUsername(username).orElseThrow();

        long followers = followRepo.countByFollowing(user);
        long following = followRepo.countByFollower(user);

        return new ProfileResponse(
            user.getUsername(),
            followers,
            following
        );
    }

    // User posts
    @GetMapping("/{username}/posts")
    public List<Post> posts(@PathVariable String username) {
        User user = userRepo.findByUsername(username).orElseThrow();
        return postRepo.findByUser(user);
    }
}
