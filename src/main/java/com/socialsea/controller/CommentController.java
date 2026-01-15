package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import com.socialsea.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comments")
@CrossOrigin("https://socialsea.netlify.app")
public class CommentController {

    private final CommentRepository commentRepo;
    private final UserRepository userRepo;
    private final PostRepository postRepo;
    private final NotificationService notificationService;

    public CommentController(
        CommentRepository commentRepo,
        UserRepository userRepo,
        PostRepository postRepo,
        NotificationService notificationService
    ) {
        this.commentRepo = commentRepo;
        this.userRepo = userRepo;
        this.postRepo = postRepo;
        this.notificationService = notificationService;
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

        Comment saved = commentRepo.save(c);

        // 🔔 Notify post owner (not yourself)
        if (!post.getUser().getId().equals(user.getId())) {
            notificationService.notify(
                post.getUser(),
                user.getUsername() + " commented on your post"
            );
        }

        return saved;
    }

    @GetMapping("/{postId}")
    public List<Comment> list(@PathVariable Long postId) {
        Post post = postRepo.findById(postId).orElseThrow();
        return commentRepo.findByPost(post);
    }
}
