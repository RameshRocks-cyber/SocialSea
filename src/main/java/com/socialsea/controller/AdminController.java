package com.socialsea.controller;

import com.socialsea.model.AnonymousPost;
import com.socialsea.model.User;
import com.socialsea.repository.AnonymousPostRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.UserService;
import com.socialsea.service.AnonymousPostService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@CrossOrigin("https://socialsea.netlify.app")
public class AdminController {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final AnonymousPostRepository anonRepo;
    private final AnonymousPostService anonymousPostService;
    private final UserService userService;

    public AdminController(
        UserRepository userRepository,
        PostRepository postRepository,
        AnonymousPostRepository anonRepo,
        AnonymousPostService anonymousPostService,
        UserService userService
    ) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.anonRepo = anonRepo;
        this.anonymousPostService = anonymousPostService;
        this.userService = userService;
    }

    // 👤 All users
    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // 🚫 Ban user
    @PutMapping("/users/{id}/ban")
    public void banUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        userService.banUser(user.getEmail());
    }

    // ✅ Unban user
    @PutMapping("/users/{id}/unban")
    public void unbanUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        userService.unbanUser(user.getEmail());
    }

    // 🗑 Delete post
    @DeleteMapping("/posts/{id}")
    public void deletePost(@PathVariable Long id) {
        postRepository.deleteById(id);
    }

    @PutMapping("/anonymous/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        AnonymousPost post = anonRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        post.setApproved(true);
        anonRepo.save(post);

        return ResponseEntity.ok("Approved");
    }

    @DeleteMapping("/anonymous/{id}")
    public ResponseEntity<?> reject(@PathVariable Long id) {
        anonRepo.deleteById(id);
        return ResponseEntity.ok("Deleted");
    }

    @PostMapping("/anonymous/bulk-approve")
    public ResponseEntity<?> bulkApprove(@RequestBody List<Long> postIds) {
        anonymousPostService.bulkApprove(postIds);
        return ResponseEntity.ok("Posts approved successfully");
    }

    @PostMapping("/anonymous/bulk-reject")
    public ResponseEntity<?> bulkReject(
            @RequestParam String reason,
            @RequestBody List<Long> postIds
    ) {
        anonymousPostService.bulkReject(postIds, reason);
        return ResponseEntity.ok("Posts rejected successfully");
    }
}
