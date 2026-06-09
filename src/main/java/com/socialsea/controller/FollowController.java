package com.socialsea.controller;

import com.socialsea.model.Follow;
import com.socialsea.model.FollowRequest;
import com.socialsea.model.User;
import com.socialsea.dto.PublicUserDto;
import com.socialsea.repository.FollowRepository;
import com.socialsea.repository.FollowRequestRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.NotificationService;
import com.socialsea.util.PublicUserPayloads;
import com.socialsea.util.UrlUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/follow")
@CrossOrigin(origins = {
        "https://socialsea.netlify.app",
        "https://socialsea.co.in",
        "https://www.socialsea.co.in",
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://43.205.229.211:5173"
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
                    PublicUserPayloads.publicDisplayName(follower) + " requested to follow you"
            );
            return Map.of("status", "REQUESTED", "message", "Follow request sent", "requestId", request.getId());
        }

        followRepo.save(new Follow(null, follower, following));
        notificationService.notifyUser(
                following.getEmail(),
                PublicUserPayloads.publicDisplayName(follower) + " started following you"
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
                PublicUserPayloads.publicDisplayName(follower) + " requested to follow you"
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
                PublicUserPayloads.publicDisplayName(receiver) + " accepted your follow request"
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
    public List<PublicUserDto> followerUsers(
            @PathVariable String identifier,
            Authentication auth,
            HttpServletRequest request
    ) {
        User viewer = requireAuth(auth);
        User user = resolveUser(identifier, auth);
        ensureCanViewConnectionList(viewer, user);
        List<User> users = followRepo.findByFollowing(user).stream()
                .map(Follow::getFollower)
                .filter(Objects::nonNull)
                .toList();
        return uniqueUserItemsById(request, users);
    }

    @GetMapping("/{identifier}/following/users")
    public List<PublicUserDto> followingUsers(
            @PathVariable String identifier,
            Authentication auth,
            HttpServletRequest request
    ) {
        User viewer = requireAuth(auth);
        User user = resolveUser(identifier, auth);
        ensureCanViewConnectionList(viewer, user);
        List<User> users = followRepo.findByFollower(user).stream()
                .map(Follow::getFollowing)
                .filter(Objects::nonNull)
                .toList();
        return uniqueUserItemsById(request, users);
    }

    private User requireAuth(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        return userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required"));
    }
    private User resolveUser(String identifier, Authentication auth) {
        String clean = normalizeIdentifier(identifier);
        if (!clean.isBlank()) {
            if (clean.equalsIgnoreCase("me") || clean.equalsIgnoreCase("self")) {
                if (auth == null || !auth.isAuthenticated()) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
                }
                return userRepo.findByEmail(auth.getName())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            }
        }
        if (clean.matches("\\d+")) {
            return userRepo.findById(Long.parseLong(clean))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        }
        return userRepo.findByNameIgnoreCase(clean)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String normalizeIdentifier(String identifier) {
        if (identifier == null) return "";
        String clean = identifier.trim();
        try {
            clean = URLDecoder.decode(clean, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // Keep raw identifier when URL decoding fails.
        }

        while (!clean.isEmpty() && (clean.startsWith("[") || clean.startsWith("\"") || clean.startsWith("'"))) {
            clean = clean.substring(1).trim();
        }
        while (!clean.isEmpty() && (clean.endsWith("]") || clean.endsWith("\"") || clean.endsWith("'") || clean.endsWith(","))) {
            clean = clean.substring(0, clean.length() - 1).trim();
        }
        return clean;
    }

    private List<PublicUserDto> uniqueUserItemsById(HttpServletRequest request, List<User> users) {
        Map<Long, PublicUserDto> unique = new LinkedHashMap<>();
        for (User user : users) {
            if (user == null || user.getId() == null) continue;
            unique.putIfAbsent(user.getId(), toUserItem(request, user));
        }
        return List.copyOf(unique.values());
    }

    private PublicUserDto toUserItem(HttpServletRequest request, User user) {
        return PublicUserPayloads.toUserSummary(user, UrlUtils.toAbsoluteUrl(request, user.getProfilePic()));
    }

    private void ensureCanViewConnectionList(User viewer, User owner) {
        if (owner == null || !owner.isPrivateAccount()) {
            return;
        }
        if (viewer == null || viewer.getId() == null || owner.getId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is private");
        }
        if (owner.getId().equals(viewer.getId())) {
            return;
        }
        if (!followRepo.existsByFollowerAndFollowing(viewer, owner)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is private");
        }
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
