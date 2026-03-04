package com.socialsea.controller;

import com.socialsea.model.Follow;
import com.socialsea.model.User;
import com.socialsea.repository.FollowRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/follow")
@CrossOrigin(origins = {
        "https://socialsea.netlify.app",
        "https://socialsea.co.in",
        "https://www.socialsea.co.in",
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://43.205.213.14:5173"
})
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

    @PostMapping("/{identifier}")
    public String follow(@PathVariable String identifier, Authentication auth) {
        User follower = userRepo.findByEmail(auth.getName()).orElseThrow();
        User following = resolveUser(identifier);

        if (follower.getId().equals(following.getId())) {
            return "Cannot follow yourself";
        }

        if (followRepo.existsByFollowerAndFollowing(follower, following)) {
            return "Already following";
        }

        followRepo.save(new Follow(null, follower, following));
        notificationService.notifyUser(
                following.getEmail(),
                follower.getEmail() + " started following you"
        );

        return "Followed";
    }

    @DeleteMapping("/{identifier}")
    public String unfollow(@PathVariable String identifier, Authentication auth) {
        User follower = userRepo.findByEmail(auth.getName()).orElseThrow();
        User following = resolveUser(identifier);

        followRepo.findAll().stream()
                .filter(f -> f.getFollower().equals(follower)
                        && f.getFollowing().equals(following))
                .findFirst()
                .ifPresent(followRepo::delete);

        return "Unfollowed";
    }

    @GetMapping("/{identifier}/followers")
    public long followers(@PathVariable String identifier) {
        User user = resolveUser(identifier);
        return followRepo.countByFollowing(user);
    }

    @GetMapping("/{identifier}/following")
    public long following(@PathVariable String identifier) {
        User user = resolveUser(identifier);
        return followRepo.countByFollower(user);
    }

    @GetMapping("/{identifier}/followers/users")
    public List<Map<String, Object>> followerUsers(@PathVariable String identifier) {
        User user = resolveUser(identifier);
        return followRepo.findByFollowing(user).stream()
                .map(Follow::getFollower)
                .filter(Objects::nonNull)
                .map(this::toUserItem)
                .distinct()
                .toList();
    }

    @GetMapping("/{identifier}/following/users")
    public List<Map<String, Object>> followingUsers(@PathVariable String identifier) {
        User user = resolveUser(identifier);
        return followRepo.findByFollower(user).stream()
                .map(Follow::getFollowing)
                .filter(Objects::nonNull)
                .map(this::toUserItem)
                .distinct()
                .toList();
    }

    private User resolveUser(String identifier) {
        if (identifier != null && identifier.matches("\\d+")) {
            return userRepo.findById(Long.parseLong(identifier)).orElseThrow();
        }
        return userRepo.findByEmail(identifier).orElseThrow();
    }

    private Map<String, Object> toUserItem(User user) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", user.getId());
        item.put("email", user.getEmail());
        item.put("name", (user.getName() != null && !user.getName().isBlank()) ? user.getName() : user.getEmail());
        item.put("profilePic", user.getProfilePic());
        return item;
    }
}
