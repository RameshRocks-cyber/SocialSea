package com.socialsea.controller;

import com.socialsea.model.Follow;
import com.socialsea.model.FollowRequest;
import com.socialsea.model.User;
import com.socialsea.repository.FollowRepository;
import com.socialsea.repository.FollowRequestRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.NotificationService;
import com.socialsea.util.UrlUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    private final FollowRequestRepository followRequestRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;

    public FollowController(
            FollowRepository followRepo,
            FollowRequestRepository followRequestRepo,
            UserRepository userRepo,
            NotificationService notificationService
    ) {
        this.followRepo = followRepo;
        this.followRequestRepo = followRequestRepo;
        this.userRepo = userRepo;
        this.notificationService = notificationService;
    }

    @PostMapping("/{identifier}")
    public Map<String, Object> follow(@PathVariable String identifier, Authentication auth) {
        User follower = requireAuth(auth);
        User following = resolveUser(identifier, auth);

        if (follower.getId().equals(following.getId())) {
            return Map.of("status", "ERROR", "message", "Cannot follow yourself");
        }

        if (followRepo.existsByFollowerAndFollowing(follower, following)) {
            return Map.of("status", "FOLLOWING", "message", "Already following");
        }

        if (following.isPrivateAccount()) {
            if (followRequestRepo.existsBySenderAndReceiverAndStatus(follower, following, "PENDING")) {
                return Map.of("status", "REQUESTED", "message", "Request already sent");
            }
            FollowRequest request = new FollowRequest(null, follower, following, "PENDING");
            followRequestRepo.save(request);
            notificationService.notifyUser(
                    following.getEmail(),
                    follower.getEmail() + " requested to follow you"
            );
            return Map.of("status", "REQUESTED", "message", "Follow request sent", "requestId", request.getId());
        }

        followRepo.save(new Follow(null, follower, following));
        notificationService.notifyUser(
                following.getEmail(),
                follower.getEmail() + " started following you"
        );

        return Map.of("status", "FOLLOWING", "message", "Followed");
    }

    @PostMapping("/requests/{identifier}")
    public Map<String, Object> requestFollow(@PathVariable String identifier, Authentication auth) {
        User follower = requireAuth(auth);
        User following = resolveUser(identifier, auth);

        if (follower.getId().equals(following.getId())) {
            return Map.of("status", "ERROR", "message", "Cannot follow yourself");
        }

        if (followRepo.existsByFollowerAndFollowing(follower, following)) {
            return Map.of("status", "FOLLOWING", "message", "Already following");
        }

        if (followRequestRepo.existsBySenderAndReceiverAndStatus(follower, following, "PENDING")) {
            return Map.of("status", "REQUESTED", "message", "Request already sent");
        }

        FollowRequest request = new FollowRequest(null, follower, following, "PENDING");
        followRequestRepo.save(request);
        notificationService.notifyUser(
                following.getEmail(),
                follower.getEmail() + " requested to follow you"
        );
        return Map.of("status", "REQUESTED", "message", "Follow request sent", "requestId", request.getId());
    }

    @DeleteMapping("/{identifier}")
    public String unfollow(@PathVariable String identifier, Authentication auth) {
        User follower = requireAuth(auth);
        User following = resolveUser(identifier, auth);

        followRepo.findAll().stream()
                .filter(f -> f.getFollower().equals(follower)
                        && f.getFollowing().equals(following))
                .findFirst()
                .ifPresent(followRepo::delete);

        return "Unfollowed";
    }

    @GetMapping("/requests")
    public List<Map<String, Object>> incomingRequests(Authentication auth, HttpServletRequest request) {
        User receiver = requireAuth(auth);
        return followRequestRepo.findByReceiverAndStatus(receiver, "PENDING").stream()
                .map(req -> toFollowRequestItem(request, req))
                .toList();
    }

    @GetMapping("/pending-requests")
    public List<Map<String, Object>> pendingRequests(Authentication auth, HttpServletRequest request) {
        User sender = requireAuth(auth);
        return followRequestRepo.findBySenderAndStatus(sender, "PENDING").stream()
                .map(req -> toFollowRequestItem(request, req))
                .toList();
    }

    @PostMapping("/requests/{id}/accept")
    public Map<String, Object> acceptRequest(@PathVariable Long id, Authentication auth) {
        User receiver = requireAuth(auth);
        FollowRequest request = followRequestRepo.findById(id).orElseThrow();
        if (!request.getReceiver().getId().equals(receiver.getId())) {
            return Map.of("status", "ERROR", "message", "Not allowed");
        }
        request.setStatus("ACCEPTED");
        followRequestRepo.save(request);
        if (!followRepo.existsByFollowerAndFollowing(request.getSender(), request.getReceiver())) {
            followRepo.save(new Follow(null, request.getSender(), request.getReceiver()));
        }
        notificationService.notifyUser(
                request.getSender().getEmail(),
                receiver.getEmail() + " accepted your follow request"
        );
        return Map.of("status", "ACCEPTED");
    }

    @PostMapping("/requests/{id}/reject")
    public Map<String, Object> rejectRequest(@PathVariable Long id, Authentication auth) {
        User receiver = requireAuth(auth);
        FollowRequest request = followRequestRepo.findById(id).orElseThrow();
        if (!request.getReceiver().getId().equals(receiver.getId())) {
            return Map.of("status", "ERROR", "message", "Not allowed");
        }
        request.setStatus("REJECTED");
        followRequestRepo.save(request);
        return Map.of("status", "REJECTED");
    }

    @GetMapping("/{identifier}/followers")
    public long followers(@PathVariable String identifier, Authentication auth) {
        requireAuth(auth);
        User user = resolveUser(identifier, auth);
        return followRepo.countByFollowing(user);
    }

    @GetMapping("/{identifier}/following")
    public long following(@PathVariable String identifier, Authentication auth) {
        requireAuth(auth);
        User user = resolveUser(identifier, auth);
        return followRepo.countByFollower(user);
    }

    @GetMapping("/{identifier}/followers/users")
    public List<Map<String, Object>> followerUsers(
            @PathVariable String identifier,
            Authentication auth,
            HttpServletRequest request
    ) {
        requireAuth(auth);
        User user = resolveUser(identifier, auth);
        return followRepo.findByFollowing(user).stream()
                .map(Follow::getFollower)
                .filter(Objects::nonNull)
                .map(u -> toUserItem(request, u))
                .distinct()
                .toList();
    }

    @GetMapping("/{identifier}/following/users")
    public List<Map<String, Object>> followingUsers(
            @PathVariable String identifier,
            Authentication auth,
            HttpServletRequest request
    ) {
        requireAuth(auth);
        User user = resolveUser(identifier, auth);
        return followRepo.findByFollower(user).stream()
                .map(Follow::getFollowing)
                .filter(Objects::nonNull)
                .map(u -> toUserItem(request, u))
                .distinct()
                .toList();
    }

    private User requireAuth(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        return userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required"));
    }
    private User resolveUser(String identifier, Authentication auth) {
        if (identifier != null) {
            String clean = identifier.trim();
            if (clean.equalsIgnoreCase("me") || clean.equalsIgnoreCase("self")) {
                if (auth == null || !auth.isAuthenticated()) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
                }
                return userRepo.findByEmail(auth.getName())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            }
        }
        if (identifier != null && identifier.matches("\\d+")) {
            return userRepo.findById(Long.parseLong(identifier))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        }
        return userRepo.findByEmailIgnoreCase(identifier)
                .or(() -> userRepo.findByNameIgnoreCase(identifier))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Map<String, Object> toUserItem(HttpServletRequest request, User user) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", user.getId());
        item.put("email", user.getEmail());
        item.put("name", (user.getName() != null && !user.getName().isBlank()) ? user.getName() : user.getEmail());
        String profilePicUrl = UrlUtils.toAbsoluteUrl(request, user.getProfilePic());
        item.put("profilePic", profilePicUrl);
        item.put("profilePicUrl", profilePicUrl);
        return item;
    }

    private Map<String, Object> toFollowRequestItem(HttpServletRequest httpRequest, FollowRequest request) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", request.getId());
        item.put("status", request.getStatus());
        item.put("sender", toUserItem(httpRequest, request.getSender()));
        item.put("receiver", toUserItem(httpRequest, request.getReceiver()));
        return item;
    }
}

