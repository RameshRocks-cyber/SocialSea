package com.socialsea.controller;

import com.socialsea.model.AnonymousPost;
import com.socialsea.repository.AnonymousPostRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/anonymous")
public class AnonymousPostController {

    private final AnonymousPostRepository repo;

    public AnonymousPostController(AnonymousPostRepository repo) {
        this.repo = repo;
    }

    @PostMapping("/upload")
    public AnonymousPost upload(@RequestBody AnonymousPost post) {
        post.setApproved(false);
        return repo.save(post);
    }

    @GetMapping("/feed")
    public List<AnonymousPost> feed() {
        return repo.findByApprovedTrue();
    }
}