package com.socialsea.controller;

import com.socialsea.model.Post;
import com.socialsea.model.User;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRepository userRepo;
    private final PostRepository postRepo;

    public MeController(UserRepository userRepo, PostRepository postRepo) {
        this.userRepo = userRepo;
        this.postRepo = postRepo;
    }

    @GetMapping("/posts")
    public ResponseEntity<?> myPosts(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User me = userRepo.findByEmail(auth.getName()).orElse(null);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "User not found"));
        }
        List<Post> posts = postRepo.findByUser(me)
            .stream()
            .filter(Post::isApproved)
            .toList();
        return ResponseEntity.ok(posts);
    }
}
