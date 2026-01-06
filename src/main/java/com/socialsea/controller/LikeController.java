package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/likes")
@CrossOrigin
public class LikeController {

    private final LikeRepository likeRepo;
    private final UserRepository userRepo;
    private final PostRepository postRepo;

    public LikeController(
        LikeRepository likeRepo,
        UserRepository userRepo,
        PostRepository postRepo
    ) {
        this.likeRepo = likeRepo;
        this.userRepo = userRepo;
        this.postRepo = postRepo;
    }

    @PostMapping("/{postId}")
    public String like(@PathVariable Long postId, Authentication auth) {

        User user = userRepo.findByUsername(auth.getName()).orElseThrow();
        Post post = postRepo.findById(postId).orElseThrow();

        if (likeRepo.existsByUserAndPost(user, post)) {
            return "Already liked";
        }

        Like like = new Like(null, user, post);
        likeRepo.save(like);
        return "Liked";
    }

    @GetMapping("/{postId}/count")
    public long count(@PathVariable Long postId) {
        Post post = postRepo.findById(postId).orElseThrow();
        return likeRepo.countByPost(post);
    }
}
