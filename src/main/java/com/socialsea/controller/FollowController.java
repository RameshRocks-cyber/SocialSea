package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/follow")
@CrossOrigin
public class FollowController {

    private final FollowRepository followRepo;
    private final UserRepository userRepo;

    public FollowController(FollowRepository followRepo, UserRepository userRepo) {
        this.followRepo = followRepo;
        this.userRepo = userRepo;
    }

    // Follow user
    @PostMapping("/{username}")
    public String follow(@PathVariable String username, Authentication auth) {

        User follower = userRepo.findByUsername(auth.getName()).orElseThrow();
        User following = userRepo.findByUsername(username).orElseThrow();

        if (follower.getId().equals(following.getId())) {
            return "Cannot follow yourself";
        }

        if (followRepo.existsByFollowerAndFollowing(follower, following)) {
            return "Already following";
        }

        followRepo.save(new Follow(null, follower, following));
        return "Followed";
    }

    // Unfollow user
    @DeleteMapping("/{username}")
    public String unfollow(@PathVariable String username, Authentication auth) {

        User follower = userRepo.findByUsername(auth.getName()).orElseThrow();
        User following = userRepo.findByUsername(username).orElseThrow();

        followRepo.findAll().stream()
            .filter(f -> f.getFollower().equals(follower)
                      && f.getFollowing().equals(following))
            .findFirst()
            .ifPresent(followRepo::delete);

        return "Unfollowed";
    }

    // Followers count
    @GetMapping("/{username}/followers")
    public long followers(@PathVariable String username) {
        User user = userRepo.findByUsername(username).orElseThrow();
        return followRepo.countByFollowing(user);
    }

    // Following count
    @GetMapping("/{username}/following")
    public long following(@PathVariable String username) {
        User user = userRepo.findByUsername(username).orElseThrow();
        return followRepo.countByFollower(user);
    }
}
