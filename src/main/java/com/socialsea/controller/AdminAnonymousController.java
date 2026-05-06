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
    "https://socialsea.co.in",
    "https://www.socialsea.co.in",
    "http://localhost:5173",
    "http://127.0.0.1:5173",
    "http://localhost:3000"
})
public class AdminAnonymousController {

    private final AnonymousPostRepository repository;

    @GetMapping("/pending")
    public List<AnonymousPost> getPendingPosts() {
        return repository.findByApprovedFalseAndRejectedFalse();
    }

    @GetMapping("/approved")
    public List<AnonymousPost> getApprovedPosts() {
        return repository.findByApprovedTrueOrderByCreatedAtDesc();
    }

    @GetMapping("/rejected")
    public List<AnonymousPost> getRejectedPosts() {
        return repository.findAll().stream()
                .filter(AnonymousPost::isRejected)
                .toList();
    }

    @PutMapping("/approve/{id}")
    public ResponseEntity<?> approve(@PathVariable long id) {
        AnonymousPost post = repository.findById(id).orElseThrow();
        post.setApproved(true);
        post.setRejected(false);
        repository.save(post);
        return ResponseEntity.ok("Approved");
    }

    @PostMapping("/approve/{id}")
    public ResponseEntity<?> approvePost(@PathVariable long id) {
        return approve(id);
    }

    @PutMapping("/reject/{id}")
    public ResponseEntity<?> reject(@PathVariable long id) {
        AnonymousPost post = repository.findById(id).orElseThrow();
        post.setApproved(false);
        post.setRejected(true);
        repository.save(post);
        return ResponseEntity.ok("Rejected");
    }

    @PostMapping("/reject/{id}")
    public ResponseEntity<?> rejectPost(@PathVariable long id, @RequestBody(required = false) String reason) {
        return reject(id);
    }
}

