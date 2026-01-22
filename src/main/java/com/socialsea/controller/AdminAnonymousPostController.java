package com.socialsea.controller;

import com.socialsea.model.AnonymousPost;
import com.socialsea.service.AnonymousPostService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/anonymous")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnonymousPostController {

    private final AnonymousPostService service;

    public AdminAnonymousPostController(AnonymousPostService service) {
        this.service = service;
    }

    // 1️⃣ View pending anonymous posts
    @GetMapping("/pending")
    public List<AnonymousPost> pending() {
        return service.getPendingPosts();
    }

    // 2️⃣ Approve post
    @PostMapping("/approve/{id}")
    public String approve(@PathVariable Long id) {
        service.approvePost(id);
        return "Post approved";
    }

    // 3️⃣ Reject post
    @PostMapping("/reject/{id}")
    public ResponseEntity<String> reject(
            @PathVariable Long id,
            @RequestParam String reason
    ) {
        service.reject(id, reason);
        return ResponseEntity.ok("Post rejected");
    }
}