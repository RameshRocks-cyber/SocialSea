package com.socialsea.controller;

import com.socialsea.model.AnonymousPost;
import com.socialsea.repository.AnonymousPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/anonymous")
@RequiredArgsConstructor
@CrossOrigin(origins = {
    "https://socialsea.netlify.app",
    "http://localhost:5173",
    "http://localhost:3000"
})
public class AdminAnonymousController {

    private final AnonymousPostRepository repository;

    @GetMapping("/pending")
    public List<AnonymousPost> getPendingPosts() {
        return repository.findByStatus("PENDING");
    }

    @GetMapping("/approved")
    public List<AnonymousPost> getApprovedPosts() {
        return repository.findByStatus("APPROVED");
    }

    @GetMapping("/rejected")
    public List<AnonymousPost> getRejectedPosts() {
        return repository.findByStatus("REJECTED");
    }

    @PutMapping("/approve/{id}")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        AnonymousPost post = repository.findById(id).orElseThrow();
        post.setStatus("APPROVED");
        repository.save(post);
        return ResponseEntity.ok("Approved");
    }

    @PutMapping("/reject/{id}")
    public ResponseEntity<?> reject(@PathVariable Long id) {
        AnonymousPost post = repository.findById(id).orElseThrow();
        post.setStatus("REJECTED");
        repository.save(post);
        return ResponseEntity.ok("Rejected");
    }
}