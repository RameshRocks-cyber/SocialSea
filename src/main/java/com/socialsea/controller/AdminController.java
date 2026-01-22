package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@CrossOrigin("https://socialsea.netlify.app")
public class AdminController {

    private final UserRepository userRepo;
    private final PostRepository postRepo;
    private final AnonymousPostRepository anonRepo;

    public AdminController(
        UserRepository userRepo,
        PostRepository postRepo,
        AnonymousPostRepository anonRepo
    ) {
        this.userRepo = userRepo;
        this.postRepo = postRepo;
        this.anonRepo = anonRepo;
    }

    // 👤 All users
    @GetMapping("/users")
    public List<User> users() {
        return userRepo.findAll();
    }

    // 🚫 Ban user
    @PostMapping("/ban/{userId}")
    public String banUser(@PathVariable Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setBanned(true);
        userRepo.save(user);
        return "User banned successfully";
    }

    // ✅ Unban user
    @PostMapping("/unban/{userId}")
    public String unbanUser(@PathVariable Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setBanned(false);
        userRepo.save(user);
        return "User unbanned successfully";
    }

    // 📸 All posts
    @GetMapping("/posts")
    public List<Post> posts() {
        return postRepo.findAll();
    }

    // 🆕 Pending posts (Waiting for approval)
    @GetMapping("/posts/pending")
    public List<Post> pendingPosts() {
        return postRepo.findAll().stream()
                .filter(p -> !p.isApproved())
                .toList();
    }

    // 🆕 Approve post
    @PostMapping("/posts/{id}/approve")
    public String approvePost(@PathVariable Long id) {
        Post post = postRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        post.setApproved(true);
        postRepo.save(post);
        return "Post approved";
    }

    // ❌ Delete post
    @DeleteMapping("/post/{postId}")
    public String deletePost(@PathVariable Long postId) {
        postRepo.deleteById(postId);
        return "Post deleted by admin";
    }

    // 👻 Anonymous videos
    @GetMapping("/anonymous")
    public List<AnonymousPost> anonymous() {
        return anonRepo.findAll();
    }

    @DeleteMapping("/anonymous/{id}")
    public void deleteAnon(@PathVariable Long id) {
        anonRepo.deleteById(id);
    }
}
