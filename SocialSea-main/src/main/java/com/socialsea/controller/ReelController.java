package com.socialsea.controller;

import com.socialsea.model.Post;
import com.socialsea.repository.PostRepository;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reels")
@CrossOrigin(origins = {"https://socialsea.netlify.app","https://socialsea.co.in","https://www.socialsea.co.in","http://localhost:5173","http://127.0.0.1:5173"})
public class ReelController {

    private final PostRepository postRepo;

    public ReelController(PostRepository postRepo) {
        this.postRepo = postRepo;
    }

    @GetMapping
    public List<Post> reels() {
        return postRepo.findByReelTrueOrderByCreatedAtDesc()
                .stream()
                .filter(Post::isApproved)
                .toList();
    }
}

