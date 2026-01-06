package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comments")
@CrossOrigin
public class CommentController {

    private final CommentRepository commentRepo;
    private final UserRepository userRepo;
    private final PostRepository postRepo;

    public CommentController(
        CommentRepository commentRepo,
        UserRepository userRepo,
        PostRepository postRepo
    ) {
        this.commentRepo = commentRepo;
        this.userRepo = userRepo;
        this.postRepo = postRepo;
    }

    @PostMapping("/{postId}")
    public Comment add(
        @PathVariable Long postId,
        @RequestBody String text,
        Authentication auth
    ) {
        User user = userRepo.findByUsername(auth.getName()).orElseThrow();
        Post post = postRepo.findById(postId).orElseThrow();

        Comment c = new Comment();
        c.setText(text);
        c.setUser(user);
        c.setPost(post);

        return commentRepo.save(c);
    }

    @GetMapping("/{postId}")
    public List<Comment> list(@PathVariable Long postId) {
        Post post = postRepo.findById(postId).orElseThrow();
        return commentRepo.findByPost(post);
    }
}
