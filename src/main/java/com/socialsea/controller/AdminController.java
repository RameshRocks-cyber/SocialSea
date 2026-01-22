package com.socialsea.controller;

import com.socialsea.model.AnonymousPost;
import com.socialsea.model.User;
import com.socialsea.repository.AnonymousPostRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.UserRepository;
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

    public AdminController(
        UserRepository userRepository,
        PostRepository postRepository,
        AnonymousPostRepository anonRepo
    ) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.anonRepo = anonRepo;
    }

    // 👤 All users
    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // 🚫 Ban user
    @PutMapping("/users/{id}/ban")
    public void banUser(@PathVariable Long id) {
        User user = userRepository.findById(id).orElseThrow();
        user.setBanned(true);
        userRepository.save(user);
    }

    // ✅ Unban user
    @PutMapping("/users/{id}/unban")
    public void unbanUser(@PathVariable Long id) {
        User user = userRepository.findById(id).orElseThrow();
        user.setBanned(false);
        userRepository.save(user);
    }

    // 🗑 Delete post
    @DeleteMapping("/posts/{id}")
    public void deletePost(@PathVariable Long id) {
        postRepository.deleteById(id);
    }

    // 👻 Anonymous Posts Moderation
    @GetMapping("/anonymous/pending")
    public List<AnonymousPost> pendingAnon() {
        return anonRepo.findByApprovedFalse();
    }

    @PutMapping("/anonymous/{id}/approve")
    public void approveAnon(@PathVariable Long id) {
        AnonymousPost post = anonRepo.findById(id).orElseThrow();
        post.setApproved(true);
        anonRepo.save(post);
    }

    @DeleteMapping("/anonymous/{id}")
    public void deleteAnon(@PathVariable Long id) {
        anonRepo.deleteById(id);
    }
}
