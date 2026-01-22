package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import com.socialsea.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/follow")
@CrossOrigin("https://socialsea.netlify.app")
public class FollowController {

    private final FollowRepository followRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;

    public FollowController(
        FollowRepository followRepo,
        UserRepository userRepo,
        NotificationService notificationService
    ) {
        this.followRepo = followRepo;
        this.userRepo = userRepo;
        this.notificationService = notificationService;
    }

    // ✅ FOLLOW USER
    @PostMapping("/{username}")
    public String follow(@PathVariable String username, Authentication auth) {

        User follower = userRepo.findByEmail(auth.getName()).orElseThrow();
        User following = userRepo.findByEmail(username).orElseThrow();

        if (follower.getId().equals(following.getId())) {
            return "Cannot follow yourself";
        }

        if (followRepo.existsByFollowerAndFollowing(follower, following)) {
            return "Already following";
        }

        followRepo.save(new Follow(null, follower, following));

        // 🔔 NOTIFICATION
        notificationService.notifyUser(
            following.getEmail(),
            follower.getEmail() + " started following you"
        );

        return "Followed";
    }

    // ✅ UNFOLLOW USER
    @DeleteMapping("/{username}")
    public String unfollow(@PathVariable String username, Authentication auth) {

        User follower = userRepo.findByEmail(auth.getName()).orElseThrow();
        User following = userRepo.findByEmail(username).orElseThrow();

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
        User user = userRepo.findByEmail(username).orElseThrow();
        return followRepo.countByFollowing(user);
    }

    // Following count
    @GetMapping("/{username}/following")
    public long following(@PathVariable String username) {
        User user = userRepo.findByEmail(username).orElseThrow();
        return followRepo.countByFollower(user);
    }
}
