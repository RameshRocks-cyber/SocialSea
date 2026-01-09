package com.socialsea.controller;

@RestController
@RequestMapping("/api/reels")
@CrossOrigin
public class ReelController {

    private final PostRepository postRepo;

    public ReelController(PostRepository postRepo) {
        this.postRepo = postRepo;
    }

    @GetMapping
    public List<Post> reels() {
        return postRepo.findByReelTrueOrderByCreatedAtDesc();
    }
}
